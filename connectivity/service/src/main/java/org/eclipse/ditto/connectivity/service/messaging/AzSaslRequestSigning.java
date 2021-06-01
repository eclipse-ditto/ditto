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
package org.eclipse.ditto.connectivity.service.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.service.UriEncoding;
import org.eclipse.ditto.connectivity.model.MessageSendingFailedException;
import org.eclipse.ditto.connectivity.service.messaging.httppush.RequestSigning;

import akka.NotUsed;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.headers.HttpCredentials;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

/**
 * Signing of HTTP requests and AMQP connection strings with Azure SASL.
 */
public final class AzSaslRequestSigning implements RequestSigning {

    private static final String AUTH_SCHEME = "SharedAccessSignature";

    private final String sharedKeyName;
    private final ByteString sharedKey;
    private final Duration ttl;
    @Nullable final String sr;

    private AzSaslRequestSigning(final String sharedKeyName, final ByteString sharedKey,
            final Duration ttl, @Nullable final String sr) {
        this.sharedKeyName = sharedKeyName;
        this.sharedKey = sharedKey;
        this.ttl = ttl;
        this.sr = sr;
    }

    /**
     * Create the signing algorithm of Azure SASL.
     *
     * @param sharedKeyName name of the shared key.
     * @param sharedKey value of the shared key.
     * @param ttl how long should tokens be valid after creation.
     * @return The signing algorithm.
     */
    public static AzSaslRequestSigning of(final String sharedKeyName, final String sharedKey,
            final Duration ttl, @Nullable final String sr) {
        try {
            final ByteString sharedKeyBytes = ByteString.fromArray(Base64.getDecoder().decode(sharedKey));
            return new AzSaslRequestSigning(sharedKeyName, sharedKeyBytes, ttl, sr);
        } catch (final IllegalArgumentException e) {
            throw MessageSendingFailedException.newBuilder()
                    .message("Failed to initiate Azure SASL signing algorithm.")
                    .description("The shared key is not in Base64 scheme.")
                    .build();
        }
    }

    @Override
    public Source<HttpRequest, NotUsed> sign(final HttpRequest request, final Instant timestamp) {
        final String resource = sr != null ? sr : request.getUri().toString();
        final String token = getSasToken(resource, timestamp);
        final HttpCredentials credentials = HttpCredentials.create(AUTH_SCHEME, token);
        final HttpRequest signedRequest = request.addCredentials(credentials);
        return Source.single(signedRequest);
    }

    /**
     * Get the SAS token of an Azure resource.
     *
     * @param resource The resource, usually the hostname.
     * @param timestamp Timestamp at which the token is generated.
     * @return The token.
     */
    public String getSasToken(final String resource, final Instant timestamp) {
        final long expiry = timestamp.plus(ttl).getEpochSecond();
        final String sr = UriEncoding.encodeAllButUnreserved(resource);
        final String stringToSign = sr + "\n" + expiry;
        final String signature = UriEncoding.encodeAllButUnreserved(
                Base64.getEncoder().encodeToString(RequestSigning.hmacSha256(sharedKey.toArray(), stringToSign)));
        return assembleToken(sr, signature, expiry, sharedKeyName);
    }

    private static String assembleToken(final String sr, final String signature, final long expiry, final String skn) {
        return String.format("sr=%s&sig=%s&se=%d&skn=%s", sr, signature, expiry, skn);
    }
}
