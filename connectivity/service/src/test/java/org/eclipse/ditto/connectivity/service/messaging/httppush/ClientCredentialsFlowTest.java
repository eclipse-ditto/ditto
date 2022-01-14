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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.assertj.core.api.Condition;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.InfoProviderFactory;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CacheFactory;
import org.eclipse.ditto.internal.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.jwt.model.JwtInvalidException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.typesafe.config.ConfigFactory;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.Pair;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;
import scala.util.Try;

/**
 * Tests {@link ClientCredentialsFlow}.
 */
public final class ClientCredentialsFlowTest {

    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofSeconds(10);
    private static final DefaultCacheConfig CACHE_CONFIG = DefaultCacheConfig.of(ConfigFactory.parseMap(Map.of(
            "cache.maximum-size", "1")), "cache");
    private static final HttpPushContext PUSH_CONTEXT = new TestHttpPushContext();
    private static final HttpRequest HTTP_REQUEST = HttpRequest.create().withMethod(HttpMethods.GET);

    private static ActorSystem actorSystem;
    private Flow<Pair<HttpRequest, HttpPushContext>, Pair<HttpRequest, HttpPushContext>, NotUsed> credentialsFlow;
    private AtomicLong asyncLoadCounter;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem");
    }

    @Before
    public void init() throws Exception {
        asyncLoadCounter = new AtomicLong();
        final ClientCredentialsFlow underTest = initClientCredentialsFlow(DEFAULT_TOKEN_TTL, Duration.ZERO);
        credentialsFlow = underTest.getFlow();
    }

    private ClientCredentialsFlow initClientCredentialsFlow(final Duration tokenTtl, final Duration maxClockSkew) {
        final Flow<HttpRequest, Try<HttpResponse>, NotUsed> flow =
                TokenFlowFactory.getFlow(tokenTtl).wireTap(t -> asyncLoadCounter.incrementAndGet());
        final AsyncCacheLoader<String, JsonWebToken>
                asyncJwtLoader = new AsyncJwtLoader(actorSystem, flow, TokenFlowFactory.CREDENTIALS);
        final ClientCredentialsFlowVisitor.JsonWebTokenExpiry jsonWebTokenExpiry =
                ClientCredentialsFlowVisitor.JsonWebTokenExpiry.of(maxClockSkew);
        final Cache<String, JsonWebToken> cache =
                CacheFactory.createCache(asyncJwtLoader, jsonWebTokenExpiry, CACHE_CONFIG, "token-cache",
                        actorSystem.getDispatcher());
        return ClientCredentialsFlow.of(cache);
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void sendRequest() {
        final CompletionStage<Pair<HttpRequest, HttpPushContext>> resultFuture =
                Source.single(Pair.create(HTTP_REQUEST, PUSH_CONTEXT))
                        .via(credentialsFlow)
                        .runWith(Sink.head(), actorSystem);

        final Pair<HttpRequest, HttpPushContext> result = resultFuture.toCompletableFuture().join();

        assertThat(result.first().getHeaders()).haveExactly(1,
                new Condition<>(h -> "Authorization".equals(h.name()) && h.value().startsWith("Bearer"),
                        "contains bearer token"));
    }

    @Test
    public void reuseToken() {

        // no token requested before first request is processed
        assertThat(asyncLoadCounter).hasValue(0);

        final CompletionStage<List<Pair<HttpRequest, HttpPushContext>>> resultFuture =
                Source.repeat(Pair.create(HTTP_REQUEST, PUSH_CONTEXT))
                        .take(5)
                        .via(credentialsFlow)
                        .runWith(Sink.seq(), actorSystem);

        final List<Pair<HttpRequest, HttpPushContext>> result = resultFuture.toCompletableFuture().join();
        assertThat(result).hasSize(5);

        final HttpHeader expectedHeader =
                result.get(0).first().getHeader("Authorization").orElseThrow();
        assertThat(result).allSatisfy(
                pair -> assertThat(pair.first().getHeader("Authorization")).contains(expectedHeader));

        // 1 token was requested for multiple requests within expiration time
        assertThat(asyncLoadCounter).hasValue(1);
    }

    @Test
    public void testTokenExpiry() {
        final Duration tokenTtl = Duration.ofSeconds(2);
        final ClientCredentialsFlow clientCredentialsFlow = initClientCredentialsFlow(tokenTtl, Duration.ZERO);

        // make 10 requests with delay of 300 milliseconds
        final int tokens = 10;
        final CompletionStage<List<Pair<HttpRequest, HttpPushContext>>> resultFuture =
                Source.repeat(Pair.create(HTTP_REQUEST, PUSH_CONTEXT))
                        .take(tokens)
                        .via(clientCredentialsFlow.getFlow())
                        .throttle(1, Duration.ofMillis(300))
                        .runWith(Sink.seq(), actorSystem);

        final List<Pair<HttpRequest, HttpPushContext>> result = resultFuture.toCompletableFuture().join();
        assertThat(result).hasSize(tokens);

        final Map<String, List<Pair<HttpRequest, HttpPushContext>>> requestsGroupedByToken = result.stream()
                .collect(Collectors.groupingBy(
                        p -> p.first().getHeader("Authorization").map(HttpHeader::value).orElseThrow()));

        // verification is fuzzy because of timing issues
        // expect max. 3 requested tokens within 3 seconds (max. 2 reloads within 3 seconds with exp. time of 2
        // seconds + 1 requested for the initial load)
        assertThat(asyncLoadCounter).hasValueBetween(2, 3);
        assertThat(requestsGroupedByToken).hasSizeBetween(2, 3);
    }

    @Test
    public void testCacheReturnsEmptyOptional() {
        final Cache<String, JsonWebToken> emptyCache = Mockito.mock(Cache.class);
        when(emptyCache.get(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        final Flow<Pair<HttpRequest, HttpPushContext>, Pair<HttpRequest, HttpPushContext>, NotUsed>
                emptyCredentialsFlow =
                ClientCredentialsFlow.of(emptyCache).getFlow();

        final CompletionStage<Pair<HttpRequest, HttpPushContext>> resultFuture =
                Source.repeat(Pair.create(HTTP_REQUEST, PUSH_CONTEXT))
                        .take(5)
                        .via(emptyCredentialsFlow)
                        .runWith(Sink.head(), actorSystem);

        assertThatExceptionOfType(CompletionException.class)
                .isThrownBy(resultFuture.toCompletableFuture()::join)
                .withCauseInstanceOf(JwtInvalidException.class);
    }

    private static class TestHttpPushContext implements HttpPushContext {

        @Override
        public ConnectionMonitor.InfoProvider getInfoProvider() {
            return InfoProviderFactory.empty();
        }

        @Override
        public void onResponse(final Try<HttpResponse> response) {
            // no-op
        }
    }
}
