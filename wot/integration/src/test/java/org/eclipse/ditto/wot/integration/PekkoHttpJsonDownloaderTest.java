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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.internal.utils.config.http.DefaultHttpProxyBaseConfig;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.wot.api.config.DefaultWotHostValidationConfig;
import org.eclipse.ditto.wot.api.config.WotConfig;
import org.eclipse.ditto.wot.model.WotThingModelNotAccessibleException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Integration test for {@link PekkoHttpJsonDownloader} verifying that SSRF host validation blocks fetches to internal
 * addresses at the download sink.
 */
public final class PekkoHttpJsonDownloaderTest {

    private ActorSystem actorSystem;

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create(getClass().getSimpleName(), ConfigFactory.empty());
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            actorSystem.terminate();
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

    @Test
    public void loopbackFetchIsBlockedByDefault() throws Exception {
        final PekkoHttpJsonDownloader underTest = downloaderWithSecurity("");
        final URL url = new URL("http://127.0.0.1:1/model.tm.jsonld");

        final CompletionStage<JsonObject> result = underTest.downloadJsonViaHttp(url, actorSystem.dispatcher());

        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(() -> result.toCompletableFuture().get())
                .withCauseInstanceOf(WotThingModelNotAccessibleException.class);
    }

    @Test
    public void linkLocalMetadataFetchIsBlockedByDefault() throws Exception {
        final PekkoHttpJsonDownloader underTest = downloaderWithSecurity("");
        final URL url = new URL("http://169.254.169.254/latest/meta-data/");

        final CompletionStage<JsonObject> result = underTest.downloadJsonViaHttp(url, actorSystem.dispatcher());

        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(() -> result.toCompletableFuture().get())
                .withCauseInstanceOf(WotThingModelNotAccessibleException.class);
    }
}
