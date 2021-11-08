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

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.service.UriEncoding;
import org.eclipse.ditto.connectivity.model.OAuthClientCredentials;
import org.eclipse.ditto.connectivity.service.config.HttpPushConfig;
import org.eclipse.ditto.internal.utils.akka.controlflow.LazyZip;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.jwt.model.ImmutableJsonWebToken;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.jwt.model.JwtInvalidException;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.MediaRanges;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.headers.Accept;
import akka.http.javadsl.model.headers.HttpCredentials;
import akka.japi.Pair;
import akka.stream.FlowShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

/**
 * Flow of HTTP requests that authenticate via client-credentials flow.
 */
public final class ClientCredentialsFlow {

    private static final JsonFieldDefinition<String> ACCESS_TOKEN =
            JsonFactory.newStringFieldDefinition("access_token");
    private static final HttpHeader ACCEPT_JSON = Accept.create(MediaRanges.create(MediaTypes.APPLICATION_JSON));

    private final HttpRequest tokenRequest;
    private final Duration maxClockSkew;

    ClientCredentialsFlow(final String tokenEndpoint, final String clientId, final String clientSecret,
            final String scope, final Duration maxClockSkew) {
        tokenRequest = toTokenRequest(tokenEndpoint, clientId, clientSecret, scope);
        this.maxClockSkew = maxClockSkew;
    }

    /**
     * Create a {@code ClientCredentialsFlow} object.
     *
     * @param credentials the credentials.
     * @param config the HTTP-Push config.
     * @return the object.
     */
    public static ClientCredentialsFlow of(final OAuthClientCredentials credentials, final HttpPushConfig config) {
        return new ClientCredentialsFlow(credentials.getTokenEndpoint(), credentials.getClientId(),
                credentials.getClientSecret(), credentials.getRequestedScopes(),
                config.getOAuth2Config().getMaxClockSkew());
    }

    /**
     * Augment HTTP requests with OAuth2 bearer tokens.
     *
     * @param actorSystem the actor system.
     * @param isStrict whether to request a token right away. Useful for connection test.
     * @return The request-augmenting flow.
     */
    public Flow<Pair<HttpRequest, HttpPushContext>, Pair<HttpRequest, HttpPushContext>, NotUsed> withToken(
            final ActorSystem actorSystem,
            final boolean isStrict) {

        final var http = Http.get(actorSystem);
        final var httpFlow = Flow.<HttpRequest>create()
                .mapAsync(1, request -> http.singleRequest(request)
                        .<Try<HttpResponse>>thenApply(Success::new)
                        .exceptionally(Failure::new)
                );
        return fromFlowWithToken(httpFlow, isStrict);
    }

    @SuppressWarnings("unchecked")
    Flow<Pair<HttpRequest, HttpPushContext>, Pair<HttpRequest, HttpPushContext>, NotUsed> fromFlowWithToken(
            final Flow<HttpRequest, Try<HttpResponse>, ?> httpFlow,
            final boolean isStrict) {

        final var flow = Flow.<Pair<HttpRequest, HttpPushContext>>create();
        if (isStrict) {
            return flow.zip(getTokenSource(httpFlow))
                    .map(ClientCredentialsFlow::augmentRequestWithJwt);
        } else {
            final var tokenSource = Source.lazySource(() -> getTokenSource(httpFlow));
            final var lazyZip = LazyZip.<Pair<HttpRequest, HttpPushContext>, JsonWebToken>of();
            return Flow.fromGraph(GraphDSL.create(builder ->
                    {
                        final var requests = builder.add(flow);
                        final var tokens = builder.add(tokenSource);
                        final var zip = builder.add(lazyZip);
                        builder.from(requests.out()).toInlet(zip.in0());
                        builder.from(tokens.out()).toInlet(zip.in1());
                        return FlowShape.of(requests.in(), zip.out());
                    }))
                    .map(ClientCredentialsFlow::augmentRequestWithJwt);
        }
    }

    /**
     * Create an infinite source of OAuth2 tokens valid at the moment of request. The source fails if the token endpoint
     * is not reachable, the request is rejected, or the token in the response is invalid.
     *
     * @param httpFlow Flow to send HTTP requests.
     * @return An infinite source of valid OAuth2 tokens, or a failed source.
     */
    Source<JsonWebToken, NotUsed> getTokenSource(final Flow<HttpRequest, Try<HttpResponse>, ?> httpFlow) {

        final var singleTokenSource = getSingleTokenSource(httpFlow);
        return singleTokenSource.flatMapConcat(token ->
                // Keep at least 1 token to fail fast when token endpoint returns an expired token.
                Source.single(token).concatLazy(
                        Source.repeat(token)
                                .takeWhile(this::shouldNotRefresh)
                                .concatLazy(Source.lazySource(() -> getTokenSource(httpFlow)))
                )
        );
    }

    /**
     * Create a source for a single OAuth2 token. Fail with a misconfiguration error if the token endpoint is not
     * reachable, the request is rejected, or the token in the response is invalid.
     *
     * @param httpFlow Flow to send HTTP requests.
     * @return Source of a single token, or a failed source.
     */
    Source<JsonWebToken, NotUsed> getSingleTokenSource(final Flow<HttpRequest, Try<HttpResponse>, ?> httpFlow) {
        return Source.single(tokenRequest)
                .via(httpFlow)
                .flatMapConcat(this::asJsonWebToken);
    }

    private boolean shouldNotRefresh(final JsonWebToken jwt) {
        return jwt.getExpirationTime().minus(maxClockSkew).isAfter(Instant.now());
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
            if (e instanceof JwtInvalidException) {
                jwtInvalid = (JwtInvalidException) e;
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
        if (error instanceof JwtInvalidException) {
            return (JwtInvalidException) error;
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

    private static HttpRequest toTokenRequest(final String tokenEndpoint,
            final String clientId,
            final String clientSecret,
            final String scope) {

        final var body = asFormEncoded(clientId, clientSecret, scope);
        final var entity = HttpEntities.create(ContentTypes.APPLICATION_X_WWW_FORM_URLENCODED, body);
        return HttpRequest.POST(tokenEndpoint).withEntity(entity).addHeader(ACCEPT_JSON);
    }

    private static String asFormEncoded(final String clientId, final String clientSecret, final String scope) {
        return String.format("grant_type=client_credentials" +
                        "&client_id=%s" +
                        "&client_secret=%s" +
                        "&scope=%s",
                UriEncoding.encodeAllButUnreserved(clientId),
                UriEncoding.encodeAllButUnreserved(clientSecret),
                UriEncoding.encodeAllButUnreserved(scope)
        );
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
