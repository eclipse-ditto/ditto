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


import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.jwt.model.JwtInvalidException;

import akka.NotUsed;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.headers.HttpCredentials;
import akka.japi.Pair;
import akka.stream.javadsl.Flow;

/**
 * Flow of HTTP requests that authenticate via client-credentials flow.
 */
public final class ClientCredentialsFlow {

    private static final String TOKEN_KEY = "token";
    private final Cache<String, JsonWebToken> tokenCache;

    ClientCredentialsFlow(final Cache<String, JsonWebToken> tokenCache) {
        this.tokenCache = tokenCache;
    }

    /**
     * Create a {@code ClientCredentialsFlow} object.
     *
     * @param tokenCache the cache that holds valid tokens and refreshes them when required.
     * @return the object.
     */
    public static ClientCredentialsFlow of(final Cache<String, JsonWebToken> tokenCache) {
        return new ClientCredentialsFlow(tokenCache);
    }

    Flow<Pair<HttpRequest, HttpPushContext>, Pair<HttpRequest, HttpPushContext>, NotUsed> getFlow() {
        final var flow = Flow.<Pair<HttpRequest, HttpPushContext>>create();
        return flow.mapAsync(1, httpRequest -> tokenCache.get(TOKEN_KEY)
                        .thenApply(jwt -> Pair.create(httpRequest, jwt.orElseThrow(
                                () -> JwtInvalidException.newBuilder().message("Failed to retrieve JSON Web Token.").build()))))
                .map(ClientCredentialsFlow::augmentRequestWithJwt);
    }

    private static Pair<HttpRequest, HttpPushContext> augmentRequestWithJwt(
            final Pair<Pair<HttpRequest, HttpPushContext>, JsonWebToken> pair) {
        final var jwt = pair.second();
        final var context = pair.first().second();
        final var augmentedRequest =
                pair.first().first().addCredentials(HttpCredentials.createOAuth2BearerToken(jwt.getToken()));
        return Pair.create(augmentedRequest, context);
    }
}
