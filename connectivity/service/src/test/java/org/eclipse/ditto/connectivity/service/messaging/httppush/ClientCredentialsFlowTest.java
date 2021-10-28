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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import org.eclipse.ditto.jwt.model.ImmutableJsonWebToken;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.jwt.model.JwtInvalidException;
import org.junit.After;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Query;
import akka.http.javadsl.model.Uri;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.MergeHub;
import akka.stream.javadsl.PartitionHub;
import akka.stream.javadsl.Sink;
import akka.stream.testkit.TestPublisher;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.stream.testkit.javadsl.TestSource;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;
import scala.util.Try;

/**
 * Tests {@link ClientCredentialsFlow}.
 */
public final class ClientCredentialsFlowTest {

    private static final String URI = "http://localhost:1234/token";
    private static final String CLIENT_ID = "client_1234";
    private static final String CLIENT_SECRET = "secret_1234";
    private static final String CLIENT_SCOPE = "scope_1234";

    private final ActorSystem actorSystem;
    private final Flow<HttpRequest, Try<HttpResponse>, ?> httpFlow;
    private final TestPublisher.Probe<Try<HttpResponse>> responseProbe;
    private final TestSubscriber.Probe<HttpRequest> requestProbe;

    @SuppressWarnings("unchecked")
    public ClientCredentialsFlowTest() {
        actorSystem = ActorSystem.create();
        final var sourcePair =
                TestSource.<Try<HttpResponse>>probe(actorSystem).preMaterialize(actorSystem);
        final var sinkPair =
                TestSink.<HttpRequest>probe(actorSystem).preMaterialize(actorSystem);
        final var partitionHub = PartitionHub.of(Object.class, (n, e) -> n - 1, 1);
        final var source =
                sourcePair.second().<Object>map(x -> x).runWith(partitionHub, actorSystem);
        final var sink =
                MergeHub.of(HttpRequest.class).toMat(sinkPair.second(), Keep.left()).run(actorSystem);
        httpFlow = Flow.<HttpRequest>create()
                .via(Flow.fromSinkAndSource(sink, source))
                .map(o -> (Try<HttpResponse>) o);
        responseProbe = sourcePair.first();
        requestProbe = sinkPair.first();
    }

    @After
    public void shutdown() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Test
    public void sendRequest() {
        final var underTest = newClientCredentialsFlow(Duration.ZERO);
        underTest.getSingleTokenSource(httpFlow).runWith(Sink.ignore(), actorSystem);
        requestProbe.ensureSubscription();
        requestProbe.request(10L);
        final var request = requestProbe.expectNext();
        assertThat(request.method()).isEqualTo(HttpMethods.POST);
        assertThat(request.getUri()).isEqualTo(Uri.create(URI));
        final var body = ((HttpEntity.Strict) request.entity()).getData().utf8String();
        final var query = Query.create(body);
        assertThat(query.get("grant_type")).contains("client_credentials");
        assertThat(query.get("client_id")).contains(CLIENT_ID);
        assertThat(query.get("client_secret")).contains(CLIENT_SECRET);
        assertThat(query.get("scope")).contains(CLIENT_SCOPE);
    }

    @Test
    public void reuseToken() {
        final var underTest = newClientCredentialsFlow(Duration.ZERO);
        final var resultFuture = underTest.getTokenSource(httpFlow)
                .take(10L)
                .runWith(Sink.seq(), actorSystem);
        requestProbe.ensureSubscription();
        requestProbe.request(10L);
        requestProbe.expectNext();
        responseProbe.ensureSubscription();
        responseProbe.expectRequest();
        final var ttl = Duration.ofHours(1L);
        final var token = getToken(ttl);
        responseProbe.sendNext(Try.apply(() -> getTokenResponse(ttl, token)));
        final var result = resultFuture.toCompletableFuture().join();
        final var expectedTokens = new JsonWebToken[10];
        Arrays.fill(expectedTokens, ImmutableJsonWebToken.fromToken(token));
        assertThat(result).isEqualTo(List.of(expectedTokens));
    }

    @Test
    public void expiredTokensAreUsedExactlyOnce() {
        final var tokens = 10L;
        final var underTest = newClientCredentialsFlow(Duration.ofHours(1L));
        final var resultFuture = underTest.getTokenSource(httpFlow)
                .take(tokens)
                .runWith(Sink.seq(), actorSystem);
        requestProbe.ensureSubscription();
        requestProbe.request(tokens * 2);
        responseProbe.ensureSubscription();
        long j = 0L;
        for (long i = 0L; i < tokens; ++i) {
            if (j <= i) {
                j += responseProbe.expectRequest();
            }
            requestProbe.expectNext(FiniteDuration.apply(10, "s"));
            final var ttl = Duration.ZERO;
            final var token = getToken(ttl, i);
            responseProbe.sendNext(Try.apply(() -> getTokenResponse(ttl, token)));
        }
        final var result = resultFuture.toCompletableFuture().join();
        final var uniqueTokens = Set.copyOf(result);
        assertThat(uniqueTokens.size()).isEqualTo(tokens);
        assertThat(uniqueTokens).containsExactlyInAnyOrderElementsOf(result);
    }

    @Test
    public void failedRequest() {
        final var underTest = newClientCredentialsFlow(Duration.ZERO);
        final var result = underTest.getSingleTokenSource(httpFlow).runWith(Sink.ignore(), actorSystem);
        responseProbe.expectRequest();
        responseProbe.sendNext(Try.apply(() -> {
            throw new IllegalStateException("Expected error");
        }));
        assertThatExceptionOfType(CompletionException.class)
                .isThrownBy(result.toCompletableFuture()::join)
                .withCauseInstanceOf(JwtInvalidException.class);
    }

    @Test
    public void incorrectStatusCode() {
        final var underTest = newClientCredentialsFlow(Duration.ZERO);
        final var result = underTest.getSingleTokenSource(httpFlow).runWith(Sink.ignore(), actorSystem);
        responseProbe.expectRequest();
        responseProbe.sendNext(Try.apply(() -> HttpResponse.create().withStatus(400)));
        assertThatExceptionOfType(CompletionException.class)
                .isThrownBy(result.toCompletableFuture()::join)
                .withCauseInstanceOf(JwtInvalidException.class);
    }

    @Test
    public void incorrectContentType() {
        final var underTest = newClientCredentialsFlow(Duration.ZERO);
        final var result = underTest.getSingleTokenSource(httpFlow).runWith(Sink.ignore(), actorSystem);
        responseProbe.expectRequest();
        responseProbe.sendNext(Try.apply(() -> HttpResponse.create().withStatus(200)
                .withEntity(HttpEntities.create(ContentTypes.TEXT_PLAIN_UTF8, "hello world"))));
        assertThatExceptionOfType(CompletionException.class)
                .isThrownBy(result.toCompletableFuture()::join)
                .withCauseInstanceOf(JwtInvalidException.class);
    }

    @Test
    public void invalidJson() {
        final var underTest = newClientCredentialsFlow(Duration.ZERO);
        final var result = underTest.getSingleTokenSource(httpFlow).runWith(Sink.ignore(), actorSystem);
        responseProbe.expectRequest();
        responseProbe.sendNext(Try.apply(() -> HttpResponse.create().withStatus(200)
                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, "hello world"))));
        assertThatExceptionOfType(CompletionException.class)
                .isThrownBy(result.toCompletableFuture()::join)
                .withCauseInstanceOf(JwtInvalidException.class);
    }

    @Test
    public void invalidJwt() {
        final var underTest = newClientCredentialsFlow(Duration.ZERO);
        final var result = underTest.getSingleTokenSource(httpFlow).runWith(Sink.ignore(), actorSystem);
        responseProbe.expectRequest();
        responseProbe.sendNext(Try.apply(() -> getTokenResponse(Duration.ZERO, "one!.invalid!.token")));
        assertThatExceptionOfType(CompletionException.class)
                .isThrownBy(result.toCompletableFuture()::join)
                .withCauseInstanceOf(JwtInvalidException.class);
    }

    private static ClientCredentialsFlow newClientCredentialsFlow(final Duration maxClockSkew) {
        return new ClientCredentialsFlow(URI, CLIENT_ID, CLIENT_SECRET, CLIENT_SCOPE, maxClockSkew);
    }

    private static HttpResponse getTokenResponse(final Duration ttl, final String token) {
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
        return HttpResponse.create()
                .withStatus(200)
                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, response));
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
