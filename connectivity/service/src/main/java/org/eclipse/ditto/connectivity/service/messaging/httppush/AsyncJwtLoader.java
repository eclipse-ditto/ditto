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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpEntities;
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.MediaRanges;
import org.apache.pekko.http.javadsl.model.MediaTypes;
import org.apache.pekko.http.javadsl.model.headers.Accept;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.service.UriEncoding;
import org.eclipse.ditto.connectivity.model.OAuthClientCredentials;
import org.eclipse.ditto.connectivity.model.OAuthCredentials;
import org.eclipse.ditto.connectivity.model.OAuthPassword;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.jwt.model.ImmutableJsonWebToken;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.jwt.model.JwtInvalidException;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

/**
 * Implementation of AsyncCacheLoader for loading {@code JsonWebToken}s.
 */
final class AsyncJwtLoader implements AsyncCacheLoader<String, JsonWebToken> {

    private static final JsonFieldDefinition<String> ACCESS_TOKEN =
            JsonFactory.newStringFieldDefinition("access_token");
    private static final HttpHeader ACCEPT_JSON = Accept.create(MediaRanges.create(MediaTypes.APPLICATION_JSON));

    private final Flow<HttpRequest, Try<HttpResponse>, NotUsed> httpFlow;
    private final Materializer materializer;
    private final HttpRequest tokenRequest;

    AsyncJwtLoader(final ActorSystem actorSystem, final OAuthCredentials credentials) {
        this(actorSystem, buildHttpFlow(Http.get(actorSystem)), credentials);
    }

    AsyncJwtLoader(final ActorSystem actorSystem, final Flow<HttpRequest, Try<HttpResponse>, NotUsed> httpFlow,
            final OAuthCredentials credentials) {
        tokenRequest = toTokenRequest(credentials);
        materializer = Materializer.createMaterializer(actorSystem);
        this.httpFlow = httpFlow;
    }

    @Override
    public CompletableFuture<? extends JsonWebToken> asyncLoad(final String key, final Executor executor) {
        return Source.single(tokenRequest)
                .via(httpFlow)
                .flatMapConcat(this::asJsonWebToken)
                .runWith(Sink.head(), materializer).toCompletableFuture();
    }

    private Source<JsonWebToken, NotUsed> asJsonWebToken(final Try<HttpResponse> tryResponse) {
        if (tryResponse.isFailure()) {
            return Source.failed(convertException(tryResponse.failed().get()));
        } else {
            return parseJwt(tryResponse.get());
        }
    }

    private Source<JsonWebToken, NotUsed> parseJwt(final HttpResponse response) {
        final boolean areStatusAndContentTypeExpected = response.status().isSuccess() &&
                response.entity().getContentType().equals(ContentTypes.APPLICATION_JSON);
        if (areStatusAndContentTypeExpected) {
            return response.entity()
                    .getDataBytes()
                    .fold(ByteString.emptyByteString(), ByteString::concat)
                    .map(ByteString::utf8String)
                    .flatMapConcat(this::extractJwt)
                    .mapMaterializedValue(any -> NotUsed.getInstance());
        } else {
            final String description = String.format("Response status is <%d> and content type is <%s>.",
                    response.status().intValue(), response.entity().getContentType());
            return Source.failed(getJwtInvalidExceptionForResponse().description(description).build());
        }
    }

    private Source<JsonWebToken, NotUsed> extractJwt(final String body) {
        try {
            final var json = JsonObject.of(body);
            return Source.single(ImmutableJsonWebToken.fromToken(json.getValueOrThrow(ACCESS_TOKEN)));
        } catch (final NullPointerException | IllegalArgumentException | JsonRuntimeException |
                       DittoRuntimeException e) {
            final JwtInvalidException jwtInvalid;
            if (e instanceof JwtInvalidException jwtInvalidException) {
                jwtInvalid = jwtInvalidException;
            } else {
                final var bodySummary = body.length() > 100 ? body.substring(0, 100) + "..." : body;
                jwtInvalid = getJwtInvalidExceptionForResponse()
                        .description(String.format("Response body: <%s>", bodySummary))
                        .build();
            }
            return Source.failed(jwtInvalid);
        }
    }

    private JwtInvalidException convertException(final Throwable error) {
        if (error instanceof JwtInvalidException jwtInvalidException) {
            return jwtInvalidException;
        } else {
            return JwtInvalidException.newBuilder()
                    .message(String.format("Request to token endpoint <%s> failed.", tokenRequest.getUri()))
                    .description(
                            String.format("Cause: %s: %s", error.getClass().getCanonicalName(), error.getMessage()))
                    .build();
        }
    }

    private DittoRuntimeExceptionBuilder<JwtInvalidException> getJwtInvalidExceptionForResponse() {
        return JwtInvalidException.newBuilder()
                .message(String.format("Received invalid JSON web token response from <%s>.", tokenRequest.getUri()))
                .description("Please verify that the token endpoint and client credentials are correct.");
    }

    private static HttpRequest toTokenRequest(final OAuthCredentials credentials) {

        final String tokenEndpoint = credentials.getTokenEndpoint();
        final String clientId = credentials.getClientId();
        final Optional<String> clientSecret = credentials.getClientSecret();
        final String scope = credentials.getRequestedScopes();
        @Nullable final String audience = credentials.getAudience().orElse(null);

        final String body;
        if (credentials instanceof OAuthClientCredentials) {
            body = clientCredentialsGrantTypeAsFormEncoded(clientId,
                    clientSecret.orElseThrow(() -> new IllegalArgumentException("For Client Credentials flow, " +
                            "clientSecret must not be null.")
                    ),
                    scope,
                    audience
            );
        } else if (credentials instanceof OAuthPassword oAuthPassword) {
            body = passwordGrantTypeAsFormEncoded(clientId,
                    clientSecret.orElse(null),
                    scope,
                    audience,
                    oAuthPassword.getUsername(),
                    oAuthPassword.getPassword()
            );
        } else {
            throw new IllegalArgumentException("Unsupported OAuth credentials type: " + credentials.getClass());
        }

        final var entity = HttpEntities.create(ContentTypes.APPLICATION_X_WWW_FORM_URLENCODED, body);
        return HttpRequest.POST(tokenEndpoint).withEntity(entity).addHeader(ACCEPT_JSON);
    }

    private static String clientCredentialsGrantTypeAsFormEncoded(final String clientId, final String clientSecret,
            final String scope, @Nullable final String audience) {
        final String withoutAud = String.format("grant_type=client_credentials" +
                        "&client_id=%s" +
                        "&client_secret=%s" +
                        "&scope=%s",
                UriEncoding.encodeAllButUnreserved(clientId),
                UriEncoding.encodeAllButUnreserved(clientSecret),
                UriEncoding.encodeAllButUnreserved(scope)
        );

        if (audience == null) {
            return withoutAud;
        } else {
            return withoutAud + String.format("&audience=%s", UriEncoding.encodeAllButUnreserved(audience));
        }
    }

    private static String passwordGrantTypeAsFormEncoded(final String clientId,
            @Nullable final String clientSecret,
            final String scope,
            @Nullable final String audience,
            final String username,
            final String password
    ) {
        String baseForm = String.format("grant_type=password" +
                        "&client_id=%s" +
                        "&scope=%s",
                UriEncoding.encodeAllButUnreserved(clientId),
                UriEncoding.encodeAllButUnreserved(scope)
        );
        if (clientSecret != null) {
            baseForm = String.format("%s&client_secret=%s",
                    baseForm,
                    UriEncoding.encodeAllButUnreserved(clientSecret)
            );
        }
        if (audience != null) {
            baseForm = String.format("%s&audience=%s",
                    baseForm,
                    UriEncoding.encodeAllButUnreserved(audience)
            );
        }
        return String.format("%s&username=%s&password=%s",
                baseForm,
                UriEncoding.encodeAllButUnreserved(username),
                UriEncoding.encodeAllButUnreserved(password)
        );
    }

    private static Flow<HttpRequest, Try<HttpResponse>, NotUsed> buildHttpFlow(final Http http) {
        return Flow.<HttpRequest>create()
                .mapAsync(1, request -> http.singleRequest(request)
                        .<Try<HttpResponse>>thenApply(Success::new)
                        .exceptionally(Failure::new)
                );
    }

}
