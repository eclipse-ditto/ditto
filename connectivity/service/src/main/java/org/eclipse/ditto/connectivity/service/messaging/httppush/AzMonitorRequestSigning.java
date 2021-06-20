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

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

import org.eclipse.ditto.connectivity.model.MessageSendingFailedException;
import org.eclipse.ditto.connectivity.service.messaging.signing.Signing;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpMethod;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.headers.HttpCredentials;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

/**
 * Signing of HTTP requests to authenticate at Azure Monitor Data Collector.
 */
final class AzMonitorRequestSigning implements HttpRequestSigning {

    private static final String X_MS_DATE_HEADER = "x-ms-date";

    static final DateTimeFormatter X_MS_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, dd MMM uuuu HH:mm:ss zzz", Locale.ENGLISH).withZone(ZoneId.of("GMT"));

    private final ActorSystem actorSystem;
    private final String workspaceId;
    private final ByteString sharedKey;
    private final Duration timeout;

    private AzMonitorRequestSigning(final ActorSystem actorSystem, final String workspaceId,
            final ByteString sharedKey, final Duration timeout) {
        this.actorSystem = actorSystem;
        this.workspaceId = workspaceId;
        this.sharedKey = sharedKey;
        this.timeout = timeout;
    }

    static AzMonitorRequestSigning of(final ActorSystem actorSystem, final String workspaceId, final String sharedKey,
            final Duration timeout) {
        try {
            final ByteString sharedKeyBytes = ByteString.fromArray(Base64.getDecoder().decode(sharedKey));
            return new AzMonitorRequestSigning(actorSystem, workspaceId, sharedKeyBytes, timeout);
        } catch (final IllegalArgumentException e) {
            throw MessageSendingFailedException.newBuilder()
                    .message("Failed to sign HTTP request to Azure Monitor.")
                    .description("The shared key is not in Base64 scheme.")
                    .build();
        }
    }

    @Override
    public Source<HttpRequest, NotUsed> sign(final HttpRequest request, final Instant timestamp) {
        return Source.fromCompletionStage(request.entity().toStrict(timeout.toMillis(), actorSystem))
                .map(request::withEntity)
                .map(strictRequest -> {
                    final String stringToSign = getStringToSign(strictRequest, timestamp);
                    final byte[] signature = Signing.hmacSha256(sharedKey.toArray(), stringToSign);
                    final HttpHeader xMsDate = HttpHeader.parse(X_MS_DATE_HEADER, X_MS_DATE_FORMAT.format(timestamp));
                    final HttpCredentials httpCredentials = toHttpCredentials(signature);
                    return request.addHeader(xMsDate).addCredentials(httpCredentials);
                });
    }

    private HttpCredentials toHttpCredentials(final byte[] signature) {
        final String encodedSignature = Base64.getEncoder().encodeToString(signature);
        final String token = workspaceId + ":" + encodedSignature;
        return HttpCredentials.create("SharedKey", token);
    }

    static String getStringToSign(final HttpRequest strictRequest, final Instant timestamp) {
        final HttpMethod verb = strictRequest.method();
        // a strict request entity always has a content length
        final long contentLength = strictRequest.entity().getContentLengthOption().orElseThrow();
        final ContentType contentType = strictRequest.entity().getContentType();
        final String xMsDate = X_MS_DATE_HEADER + ":" + X_MS_DATE_FORMAT.format(timestamp);
        final String resource = strictRequest.getUri().path();
        return String.join("\n", verb.name(), String.valueOf(contentLength), contentType.toString(), xMsDate, resource);
    }

}
