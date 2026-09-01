/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.internal.utils.config.http.DefaultHttpProxyBaseConfig;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.wot.api.config.DefaultWotHostValidationConfig;
import org.eclipse.ditto.wot.api.config.WotConfig;
import org.eclipse.ditto.wot.model.WotThingModelNotAccessibleException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.typesafe.config.ConfigFactory;

/**
 * Integration test for {@link PekkoHttpJsonDownloader} verifying that SSRF host validation blocks fetches to internal
 * addresses at the download sink - both for the initially requested URL and for every followed redirect target.
 */
public final class PekkoHttpJsonDownloaderTest {

    /**
     * The test HTTP server is bound to loopback, which the validator blocks by default. Allow-listing it lets the
     * tests exercise the redirect handling itself, while the redirect <em>targets</em> stay subject to validation.
     */
    private static final String ALLOW_LOOPBACK = "allowed-hostnames = \"127.0.0.1\"";

    /**
     * One ActorSystem for the whole class: {@link org.eclipse.ditto.internal.utils.http.DefaultHttpClientFacade}
     * caches a JVM-wide singleton bound to the ActorSystem of its very first {@code getInstance} call. Creating a
     * fresh ActorSystem per test method would leave that singleton pointing at an already terminated system, so
     * every request after the first one would never complete.
     */
    private static ActorSystem actorSystem;

    private HttpServer httpServer;
    private String baseUrl;

    @BeforeClass
    public static void setUpClass() {
        actorSystem = ActorSystem.create(PekkoHttpJsonDownloaderTest.class.getSimpleName(), ConfigFactory.empty());
    }

    @AfterClass
    public static void tearDownClass() {
        if (actorSystem != null) {
            actorSystem.terminate();
            actorSystem = null;
        }
    }

    @Before
    public void setUp() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.start();
        baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();
    }

    @After
    public void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    private PekkoHttpJsonDownloader downloaderWithSecurity(final String securityBody) {
        final WotConfig wotConfig = mock(WotConfig.class);
        when(wotConfig.getHttpProxyConfig())
                .thenReturn(DefaultHttpProxyBaseConfig.ofHttpProxy(ConfigFactory.parseString("http.proxy { }")));
        when(wotConfig.getHostValidationConfig())
                .thenReturn(DefaultWotHostValidationConfig.of(
                        ConfigFactory.parseString("http.security {\n" + securityBody + "\n}")));
        final Executor executor = actorSystem.dispatcher();
        return new PekkoHttpJsonDownloader(actorSystem, wotConfig, executor);
    }

    private void respondWithRedirectTo(final String path, final String location, final AtomicInteger requestCounter) {
        register(path, exchange -> {
            requestCounter.incrementAndGet();
            exchange.getResponseHeaders().set("Location", location);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
    }

    private void respondWithJson(final String path, final String json) {
        register(path, exchange -> {
            final byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
    }

    private void register(final String path, final HttpHandler handler) {
        httpServer.createContext(path, handler);
    }

    private static void assertBlocked(final CompletionStage<JsonObject> result) {
        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(() -> result.toCompletableFuture().get(20, TimeUnit.SECONDS))
                .withCauseInstanceOf(WotThingModelNotAccessibleException.class);
    }

    @Test(timeout = 30_000L)
    public void loopbackFetchIsBlockedByDefault() throws Exception {
        final PekkoHttpJsonDownloader underTest = downloaderWithSecurity("");
        final URL url = new URL("http://127.0.0.1:1/model.tm.jsonld");

        assertBlocked(underTest.downloadJsonViaHttp(url, actorSystem.dispatcher()));
    }

    @Test(timeout = 30_000L)
    public void linkLocalMetadataFetchIsBlockedByDefault() throws Exception {
        final PekkoHttpJsonDownloader underTest = downloaderWithSecurity("");
        final URL url = new URL("http://169.254.169.254/latest/meta-data/");

        assertBlocked(underTest.downloadJsonViaHttp(url, actorSystem.dispatcher()));
    }

    @Test(timeout = 30_000L)
    public void redirectFromAllowedHostToLinkLocalMetadataIsBlocked() throws Exception {
        // the whole point of re-validating redirect targets: an allowed (even attacker-controlled public) host must
        // not be able to bounce the fetch to the cloud instance-metadata endpoint
        final AtomicInteger requests = new AtomicInteger();
        respondWithRedirectTo("/model.tm.jsonld", "http://169.254.169.254/latest/meta-data/", requests);
        final PekkoHttpJsonDownloader underTest = downloaderWithSecurity(ALLOW_LOOPBACK);

        assertBlocked(underTest.downloadJsonViaHttp(new URL(baseUrl + "/model.tm.jsonld"),
                actorSystem.dispatcher()));
        assertThat(requests).hasValue(1);
    }

    @Test(timeout = 30_000L)
    public void redirectFromAllowedHostToIpv6UniqueLocalMetadataIsBlocked() throws Exception {
        final AtomicInteger requests = new AtomicInteger();
        respondWithRedirectTo("/model.tm.jsonld", "http://[fd00:ec2::254]/latest/meta-data/", requests);
        final PekkoHttpJsonDownloader underTest = downloaderWithSecurity(ALLOW_LOOPBACK);

        assertBlocked(underTest.downloadJsonViaHttp(new URL(baseUrl + "/model.tm.jsonld"),
                actorSystem.dispatcher()));
        assertThat(requests).hasValue(1);
    }

    @Test(timeout = 30_000L)
    public void redirectToNonHttpSchemeIsRejected() throws Exception {
        final AtomicInteger requests = new AtomicInteger();
        respondWithRedirectTo("/model.tm.jsonld", "file:/etc/passwd", requests);
        final PekkoHttpJsonDownloader underTest = downloaderWithSecurity(ALLOW_LOOPBACK);

        assertBlocked(underTest.downloadJsonViaHttp(new URL(baseUrl + "/model.tm.jsonld"),
                actorSystem.dispatcher()));
        assertThat(requests).hasValue(1);
    }

    @Test(timeout = 30_000L)
    public void exceedingTheConfiguredMaxRedirectsIsRejected() throws Exception {
        final AtomicInteger requests = new AtomicInteger();
        respondWithRedirectTo("/loop.tm.jsonld", baseUrl + "/loop.tm.jsonld", requests);
        final PekkoHttpJsonDownloader underTest =
                downloaderWithSecurity(ALLOW_LOOPBACK + "\nmax-redirects = 2");

        assertBlocked(underTest.downloadJsonViaHttp(new URL(baseUrl + "/loop.tm.jsonld"),
                actorSystem.dispatcher()));
        // the initial request plus the 2 permitted redirect hops - the 3rd redirect is not followed any more:
        assertThat(requests).hasValue(3);
    }

    @Test(timeout = 30_000L)
    public void relativeRedirectLocationIsResolvedAgainstTheRequestedUrl() throws Exception {
        final AtomicInteger requests = new AtomicInteger();
        respondWithRedirectTo("/models/model.tm.jsonld", "/redirected/model.tm.jsonld", requests);
        respondWithJson("/redirected/model.tm.jsonld", "{\"title\":\"redirected\"}");
        final PekkoHttpJsonDownloader underTest = downloaderWithSecurity(ALLOW_LOOPBACK);

        final CompletionStage<JsonObject> result =
                underTest.downloadJsonViaHttp(new URL(baseUrl + "/models/model.tm.jsonld"),
                        actorSystem.dispatcher());

        assertThat(result.toCompletableFuture().get(20, TimeUnit.SECONDS))
                .isEqualTo(JsonObject.newBuilder().set("title", "redirected").build());
        assertThat(requests).hasValue(1);
    }
}
