/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
import java.util.Base64;
import java.util.UUID;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.connectivity.model.OAuthClientCredentials;

import akka.NotUsed;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.RequestEntity;
import akka.stream.javadsl.Flow;
import scala.util.Try;

/**
 * Factory creating token flows for different test cases.
 */
final class TokenFlowFactory {

    static final String URI = "http://localhost:1234/token";
    static final String CLIENT_ID = "client_1234";
    static final String CLIENT_SECRET = "secret_1234";
    static final String CLIENT_SCOPE = "scope_1234";
    static final OAuthClientCredentials CREDENTIALS = new OAuthClientCredentials.Builder()
            .clientId(CLIENT_ID)
            .tokenEndpoint(URI)
            .clientSecret(CLIENT_SECRET)
            .scope(CLIENT_SCOPE)
            .build();

    static Flow<HttpRequest, Try<HttpResponse>, NotUsed> getFlow(final Duration ttl) {
        return Flow.<HttpRequest>create()
                .map(r -> Try.apply(() -> getTokenResponse(ttl, getToken(ttl), HttpStatus.OK.getCode())));
    }

    static Flow<HttpRequest, Try<HttpResponse>, NotUsed> getFlow(final Duration ttl, final String token) {
        return Flow.<HttpRequest>create()
                .map(r -> Try.apply(() -> getTokenResponse(ttl, token, HttpStatus.OK.getCode())));
    }

    static Flow<HttpRequest, Try<HttpResponse>, NotUsed> getFlow(final Duration ttl, final int status) {
        return Flow.<HttpRequest>create()
                .map(r -> Try.apply(() -> getTokenResponse(ttl, getToken(ttl), status)));
    }

    static Flow<HttpRequest, Try<HttpResponse>, NotUsed> getFlow(final int status, final RequestEntity entity) {
        return Flow.<HttpRequest>create()
                .map(r -> Try.apply(() -> getTokenResponse(status, entity)));
    }

    private static HttpResponse getTokenResponse(final Duration ttl, final String token, final int status) {
        final String response = String.format("{\n" +
                        " \"access_token\":\"%s\",\n" +
                        " \"expires_in\":%d,\n" +
                        " \"scope\":\"%s\",\n" +
                        " \"token_type\":\"bearer\"\n" +
                        "}",
                token,
                Math.max(ttl.getSeconds(), 1),
                CLIENT_SCOPE
        );
        return getTokenResponse(status, HttpEntities.create(ContentTypes.APPLICATION_JSON, response));

    }

    private static HttpResponse getTokenResponse(final int status, final RequestEntity entity) {
        return HttpResponse.create()
                .withStatus(status)
                .withEntity(entity);
    }

    private static String getToken(final Duration ttl) {
        return getToken(ttl, 0);
    }

    private static String getToken(final Duration ttl, final long id) {
        final var now = Instant.now();
        final var exp = now.plus(ttl).getEpochSecond();
        final var iat = now.getEpochSecond();
        final String body = String.format("{\n" +
                        "  \"aud\": [],\n" +
                        "  \"client_id\": \"%s\",\n" +
                        "  \"exp\": %s,\n" +
                        "  \"ext\": {},\n" +
                        "  \"iat\": %d,\n" +
                        "  \"iss\": \"%s\",\n" +
                        "  \"jti\": \"%s\",\n" +
                        "  \"nbf\": %d,\n" +
                        "  \"scp\": [\"%s\"],\n" +
                        "  \"sub\": \"%s\"\n" +
                        "}",
                CLIENT_ID,
                exp,
                iat,
                URI,
                "iteration-" + id,
                iat,
                CLIENT_SCOPE,
                CLIENT_ID
        );
        final String header = "{\n" +
                "  \"alg\": \"RS256\",\n" +
                "  \"kid\": \"public:" + UUID.randomUUID() + "\",\n" +
                "  \"typ\": \"JWT\"\n" +
                "}";
        final String signature = "lorem ipsum dolor sit amet";
        final Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(header.getBytes()) + "." +
                encoder.encodeToString(body.getBytes()) + "." +
                encoder.encodeToString(signature.getBytes());
    }

}
