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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.awaitility.Awaitility;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CacheFactory;
import org.eclipse.ditto.internal.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.jwt.model.JwtInvalidException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.testkit.javadsl.TestKit;

public class AsyncJwtLoaderTest {


    private static final DefaultCacheConfig CACHE_CONFIG =
            DefaultCacheConfig.of(ConfigFactory.parseMap(Map.of("cache.maximum-size", "1")), "cache");
    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem");
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void testAsyncLoad() {
        new TestKit(actorSystem) {{
            final AsyncJwtLoader asyncJwtLoader = new AsyncJwtLoader(actorSystem, TokenFlowFactory.getFlow(TOKEN_TTL),
                    TokenFlowFactory.CREDENTIALS);
            final CompletableFuture<? extends JsonWebToken> token =
                    asyncJwtLoader.asyncLoad("token", actorSystem.dispatcher());
            final JsonWebToken jsonWebToken = token.join();
            assertThat(jsonWebToken.getSubjects()).containsOnly("client_1234");
        }};
    }

    @Test
    public void testAsyncLoadWrongResponseCode() {
        new TestKit(actorSystem) {{
            final AsyncJwtLoader asyncJwtLoader = new AsyncJwtLoader(actorSystem, TokenFlowFactory.getFlow(TOKEN_TTL,
                    400),
                    TokenFlowFactory.CREDENTIALS);

            final CompletableFuture<? extends JsonWebToken> tokenFuture =
                    asyncJwtLoader.asyncLoad("token", actorSystem.dispatcher());

            assertThatExceptionOfType(CompletionException.class)
                    .isThrownBy(tokenFuture::join)
                    .withCauseInstanceOf(JwtInvalidException.class);
        }};
    }

    @Test
    public void testAsyncLoadInvalidToken() {
        new TestKit(actorSystem) {{
            final AsyncJwtLoader asyncJwtLoader =
                    new AsyncJwtLoader(actorSystem, TokenFlowFactory.getFlow(Duration.ZERO,
                            "one!.invalid!.token"), TokenFlowFactory.CREDENTIALS);

            final CompletableFuture<? extends JsonWebToken> tokenFuture =
                    asyncJwtLoader.asyncLoad("token", actorSystem.dispatcher());

            assertThatExceptionOfType(CompletionException.class)
                    .isThrownBy(tokenFuture::join)
                    .withCauseInstanceOf(JwtInvalidException.class);
        }};
    }

    @Test
    public void testAsyncLoadWrongContentType() {
        new TestKit(actorSystem) {{
            final AsyncJwtLoader asyncJwtLoader = new AsyncJwtLoader(actorSystem,
                    TokenFlowFactory.getFlow(200, HttpEntities.create(ContentTypes.APPLICATION_JSON, "hello world")),
                    TokenFlowFactory.CREDENTIALS);

            final CompletableFuture<? extends JsonWebToken> tokenFuture =
                    asyncJwtLoader.asyncLoad("token", actorSystem.dispatcher());

            assertThatExceptionOfType(CompletionException.class)
                    .isThrownBy(tokenFuture::join)
                    .withCauseInstanceOf(JwtInvalidException.class);
        }};
    }

    @Test
    public void testGetFromCache() {
        new TestKit(actorSystem) {{
            final AsyncJwtLoader asyncJwtLoader = new AsyncJwtLoader(actorSystem, TokenFlowFactory.getFlow(TOKEN_TTL),
                    TokenFlowFactory.CREDENTIALS);
            final Cache<String, JsonWebToken> cache =
                    CacheFactory.createCache(asyncJwtLoader, CACHE_CONFIG, "token-cache", actorSystem.getDispatcher());
            final CompletableFuture<Optional<JsonWebToken>> token = cache.get("token");
            final Optional<JsonWebToken> jsonWebToken = token.join();
            assertThat(jsonWebToken.get().getSubjects()).containsOnly("client_1234");
        }};
    }

    @Test
    public void testGetFromCacheWithCustomJwtExpiration() {
        new TestKit(actorSystem) {{
            final AsyncJwtLoader asyncJwtLoader = new AsyncJwtLoader(actorSystem,
                    TokenFlowFactory.getFlow(Duration.ofSeconds(3)),
                    TokenFlowFactory.CREDENTIALS);

            final Cache<String, JsonWebToken> cache =
                    CacheFactory.createCache(asyncJwtLoader,
                            ClientCredentialsFlowVisitor.JsonWebTokenExpiry.of(Duration.ZERO),
                            CACHE_CONFIG, "token-cache", actorSystem.getDispatcher());

            final CompletableFuture<Optional<JsonWebToken>> token = cache.get("token");
            final Optional<JsonWebToken> jsonWebTokenOpt = token.join();
            assertThat(jsonWebTokenOpt).isNotEmpty();
            assertThat(jsonWebTokenOpt.get().getSubjects()).containsOnly("client_1234");

            Awaitility.await("token expired")
                    .until(() -> {
                        final Optional<JsonWebToken> token1 = cache.getIfPresent("token").join();
                        return token1.isEmpty();
                    });
        }};
    }

}
