/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.connectivity.service.messaging.httppush;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.base.service.UriEncoding;
import org.eclipse.ditto.connectivity.service.messaging.signing.Signing;
import org.eclipse.ditto.internal.utils.pubsub.ddata.Hashes;
import org.eclipse.ditto.json.JsonParseException;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.Query;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.model.headers.HttpCredentials;
import akka.japi.Pair;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

/**
 * Signing of HTTP requests to authenticate at AWS.
 */
final class AwsRequestSigning implements HttpRequestSigning {

    private static final IntPredicate UNRESERVED_OR_SLASH =
            ((IntPredicate) UriEncoding::isUnreserved).or(c -> c == '/');

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final char[] LOWER_CASE_HEX_CHARS = "0123456789abcdef".toCharArray();
    private static final String CONTENT_TYPE_HEADER = "content-type";
    private static final String X_AMZ_DATE_HEADER = "x-amz-date";
    private static final String X_AMZ_CONTENT_SHA256_HEADER = "x-amz-content-sha256";
    private static final String HOST_HEADER = "host";
    private static final DateTimeFormatter DATE_STAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("Z"));
    static final DateTimeFormatter X_AMZ_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssz").withZone(ZoneId.of("Z"));

    private final ActorSystem actorSystem;
    private final Collection<String> canonicalHeaderNames;
    private final String region;
    private final String service;
    private final String accessKey;
    private final String secretKey;
    private final boolean doubleEncodeAndNormalize;
    private final Duration timeout;
    private final XAmzContentSha256 xAmzContentSha256;

    AwsRequestSigning(final ActorSystem actorSystem, final List<String> canonicalHeaderNames,
            final String region, final String service, final String accessKey, final String secretKey,
            final boolean doubleEncodeAndNormalize, final Duration timeout,
            final XAmzContentSha256 xAmzContentSha256) {
        this.actorSystem = actorSystem;
        this.canonicalHeaderNames = toDeduplicatedSortedLowerCase(canonicalHeaderNames, xAmzContentSha256);
        this.region = region;
        this.service = service;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.doubleEncodeAndNormalize = doubleEncodeAndNormalize;
        this.timeout = timeout;
        this.xAmzContentSha256 = xAmzContentSha256;
    }

    @Override
    public Source<HttpRequest, NotUsed> sign(final HttpRequest request, final Instant timestamp) {
        return Source.fromCompletionStage(request.entity().toStrict(timeout.toMillis(), actorSystem))
                .map(request::withEntity)
                .map(strictRequest -> {
                    final byte[] key = getSigningKey(secretKey, timestamp);
                    final String payloadHash = getPayloadHash(strictRequest);
                    final String stringToSign =
                            getStringToSign(strictRequest, timestamp, doubleEncodeAndNormalize, payloadHash);
                    final String signature = toLowerCaseHex(Signing.hmacSha256(key, stringToSign));
                    final HttpRequest augmentedRequest;
                    if (xAmzContentSha256 == XAmzContentSha256.EXCLUDED) {
                        augmentedRequest = strictRequest
                                .addHeader(HttpHeader.parse(X_AMZ_DATE_HEADER, X_AMZ_DATE_FORMATTER.format(timestamp)));
                    } else {
                        augmentedRequest = strictRequest
                                .addHeader(HttpHeader.parse(X_AMZ_DATE_HEADER, X_AMZ_DATE_FORMATTER.format(timestamp)))
                                .addHeader(HttpHeader.parse(X_AMZ_CONTENT_SHA256_HEADER, payloadHash));
                    }
                    final var credentials = renderHttpCredentials(signature, timestamp);
                    return augmentedRequest.addCredentials(credentials);
                });
    }

    private HttpCredentials renderHttpCredentials(final String signature, final Instant xAmzDate) {
        final var authParams = List.of(
                Pair.create("Credential", accessKey + "/" + getCredentialScope(xAmzDate, region, service)),
                Pair.create("SignedHeaders", getSignedHeaders()),
                Pair.create("Signature", signature)
        );
        // render without quoting: AWS can't handle quoted auth params (RFC-7235) as of 26.05.2021
        final String authParamsRenderedWithoutQuotes = renderWithoutQuotes(authParams);
        return HttpCredentials.create(ALGORITHM, authParamsRenderedWithoutQuotes);
    }

    private byte[] getSigningKey(final String secretKey, final Instant timestamp) {
        return getKSigning(getKService(getKRegion(getKDate(getKSecret(secretKey), timestamp), region), service));
    }

    // for unit tests
    String getStringToSign(final HttpRequest strictRequest, final Instant xAmzDate) {
        return getStringToSign(strictRequest, xAmzDate, true, getPayloadHash(strictRequest));
    }

    private String getStringToSign(final HttpRequest strictRequest, final Instant xAmzDate,
            final boolean doubleEncodeAndNormalize, final String payloadHash) {
        return String.join("\n",
                ALGORITHM,
                X_AMZ_DATE_FORMATTER.format(xAmzDate),
                getCredentialScope(xAmzDate, region, service),
                sha256(getCanonicalRequest(strictRequest, xAmzDate, doubleEncodeAndNormalize, payloadHash)
                        .getBytes(StandardCharsets.UTF_8))
        );
    }

    String getCanonicalRequest(final HttpRequest strictRequest, final Instant xAmzDate,
            final boolean doubleEncodeAndNormalize, final String payloadHash) {
        final String method = strictRequest.method().name();
        final String canonicalUri = getCanonicalUri(strictRequest.getUri(), doubleEncodeAndNormalize);
        final String canonicalQuery = getCanonicalQuery(strictRequest.getUri().query());
        final String canonicalHeaders = getCanonicalHeaders(strictRequest, xAmzDate, payloadHash);
        return String.join("\n", method, canonicalUri, canonicalQuery, canonicalHeaders, getSignedHeaders(),
                payloadHash);
    }

    String getPayloadHash(final HttpRequest strictRequest) {
        final ByteString payload = ((HttpEntity.Strict) strictRequest.entity()).getData();
        return getPayloadHash(payload);
    }

    String getPayloadHash(final ByteString payload) {
        return xAmzContentSha256 == XAmzContentSha256.UNSIGNED ? "UNSIGNED-PAYLOAD" : sha256(payload.toArray());
    }

    String getCanonicalHeaders(final HttpRequest request, final Instant xAmzDate, final String payloadHash) {
        return canonicalHeaderNames.stream()
                .map(key -> {
                    switch (key) {
                        case HOST_HEADER:
                            return HOST_HEADER + ":" + request.getUri().host().address() + "\n";
                        case CONTENT_TYPE_HEADER:
                            return getContentTypeAsCanonicalHeader(request);
                        case X_AMZ_CONTENT_SHA256_HEADER:
                            return X_AMZ_CONTENT_SHA256_HEADER + ":" + payloadHash + "\n";
                        case X_AMZ_DATE_HEADER:
                            return X_AMZ_DATE_HEADER + ":" + X_AMZ_DATE_FORMATTER.format(xAmzDate) + "\n";
                        default:
                            return key + streamHeaders(request, key)
                                    .map(HttpHeader::value)
                                    .map(AwsRequestSigning::trimHeaderValue)
                                    .collect(Collectors.joining(",", ":", "\n"));
                    }
                })
                .collect(Collectors.joining());
    }

    private String getSignedHeaders() {
        return String.join(";", canonicalHeaderNames);
    }

    static String sha256(final byte[] bytes) {
        return toLowerCaseHex(Hashes.getSha256().digest(bytes));
    }

    static String getCanonicalQuery(final Query query) {
        return query.toMultiMap()
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(entry -> {
                    final String encodedKey = UriEncoding.encodeAllButUnreserved(entry.getKey());
                    return encodeQueryParameterValues(entry.getValue()).map(value -> encodedKey + "=" + value);
                })
                .collect(Collectors.joining("&"));
    }

    static Stream<String> encodeQueryParameterValues(final List<String> values) {
        return values.stream()
                // values are sorted by code point first
                .sorted()
                // '=' must be double-encoded
                .map(s -> s.replace("=", "%3D"))
                // all non-unreserved characters are single-encoded
                .map(UriEncoding::encodeAllButUnreserved);
    }

    static String getCanonicalUri(final Uri uri, final boolean doubleEncodeAndNormalize) {
        return doubleEncodeAndNormalize ? encodeAndNormalizePathSegments(uri) : getPathStringOrSlashWhenEmpty(uri);
    }

    private static String getPathStringOrSlashWhenEmpty(final Uri uri) {
        final String pathString = uri.getPathString();
        return pathString.isEmpty() ? "/" : UriEncoding.encodeRFC3986UriComponent(pathString, UNRESERVED_OR_SLASH);
    }

    static String encodeAndNormalizePathSegments(final Uri uri) {
        final String slash = "/";
        final List<String> segments = StreamSupport.stream(uri.pathSegments().spliterator(), false)
                .filter(s -> !s.isEmpty())
                // encode path segment twice as required for all AWS services except S3
                .map(UriEncoding::encodeAllButUnreserved)
                .map(UriEncoding::encodeAllButUnreserved)
                .collect(Collectors.toList());

        if (segments.isEmpty()) {
            return "/";
        } else {
            final String trailingString = uri.getPathString().endsWith(slash) ? slash : "";
            return segments.stream().collect(Collectors.joining(slash, slash, trailingString));
        }
    }

    static byte[] getKSecret(final String secretKey) {
        return ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
    }

    static byte[] getKDate(final byte[] kSecret, final Instant xAmzDate) {
        final String dateStamp = DATE_STAMP_FORMATTER.format(xAmzDate);
        return Signing.hmacSha256(kSecret, dateStamp);
    }

    static byte[] getKRegion(final byte[] kDate, final String region) {
        return Signing.hmacSha256(kDate, region);
    }

    static byte[] getKService(final byte[] kRegion, final String service) {
        return Signing.hmacSha256(kRegion, service);
    }

    static byte[] getKSigning(final byte[] kService) {
        return Signing.hmacSha256(kService, "aws4_request");
    }

    static Collection<String> toDeduplicatedSortedLowerCase(final List<String> strings,
            final XAmzContentSha256 xAmzContentSha256) {
        final Stream<String> headers = xAmzContentSha256 == XAmzContentSha256.EXCLUDED
                ? strings.stream()
                : Stream.concat(strings.stream(), Stream.of(X_AMZ_CONTENT_SHA256_HEADER));
        return headers.map(String::strip)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String getCredentialScope(final Instant xAmzDate, final String region, final String service) {
        return String.join("/", DATE_STAMP_FORMATTER.format(xAmzDate), region, service, "aws4_request");
    }

    private static String getContentTypeAsCanonicalHeader(final HttpRequest request) {
        final ContentType contentType =
                request.getHeader(akka.http.javadsl.model.headers.ContentType.class)
                        .map(akka.http.javadsl.model.headers.ContentType::contentType)
                        .orElse(request.entity().getContentType());
        return CONTENT_TYPE_HEADER + ":" +
                contentType.mediaType() +
                contentType.getCharsetOption().map(charset -> "; charset=" + charset.value()).orElse("") +
                "\n";
    }

    private static String trimHeaderValue(final String headerValue) {
        return headerValue.strip().replaceAll("\\s+", " ");
    }

    private static Stream<HttpHeader> streamHeaders(final HttpRequest request, final String lowerCaseHeaderName) {
        return StreamSupport.stream(request.getHeaders().spliterator(), false)
                .filter(header -> header.lowercaseName().equals(lowerCaseHeaderName));
    }

    private static String toLowerCaseHex(final byte[] bytes) {
        final StringBuilder builder = new StringBuilder(2 * bytes.length);
        for (final byte b : bytes) {
            builder.append(LOWER_CASE_HEX_CHARS[(b & 0xF0) >> 4]);
            builder.append(LOWER_CASE_HEX_CHARS[b & 0x0F]);
        }
        return builder.toString();
    }

    private static String renderWithoutQuotes(final List<Pair<String, String>> authParams) {
        return authParams.stream()
                .map(pair -> pair.first() + "=" + pair.second())
                // space after comma is obligatory
                .collect(Collectors.joining(", "));
    }

    enum XAmzContentSha256 {
        INCLUDED,
        EXCLUDED,
        UNSIGNED;

        static XAmzContentSha256 forName(final String name) {
            return Arrays.stream(values()).filter(option -> option.name().equalsIgnoreCase(name)).findAny()
                    .orElseThrow(() -> new JsonParseException("The HMAC credentials parameter 'xAmzContentSha256'" +
                            " must have one of the following as value: 'INCLUDED', 'EXCLUDED', 'UNSIGNED'."));
        }
    }
}
