/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;

import org.eclipse.ditto.internal.models.signalenrichment.DittoCachingSignalEnrichmentFacadeProvider;
import org.junit.After;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link ConnectivityCachingSignalEnrichmentProvider}.
 */
public final class ConnectivityCachingSignalEnrichmentProviderTest {

    private static final Config DISPATCHER_CONFIG = ConfigFactory.parseString("signal-enrichment-cache-dispatcher {\n" +
            "  type = Dispatcher\n" +
            "  executor = \"fork-join-executor\"\n" +
            "  fork-join-executor {\n" +
            "    parallelism-min = 4\n" +
            "    parallelism-factor = 3.0\n" +
            "    parallelism-max = 32\n" +
            "  }\n" +
            "  throughput = 5\n" +
            "}");


    private static final Config CONFIG = ConfigFactory.empty()
            .withValue("ditto.signal-enrichment.caching-signal-enrichment-facade.provider",
                    ConfigValueFactory.fromAnyRef(DittoCachingSignalEnrichmentFacadeProvider.class.getCanonicalName()))
            .withValue("ditto.connectivity.signal-enrichment.provider",
                    ConfigValueFactory.fromAnyRef(ConnectivityCachingSignalEnrichmentProvider.class.getCanonicalName()))
            .withValue("ditto.connectivity.signal-enrichment.provider-config.ask-timeout",
                    ConfigValueFactory.fromAnyRef(Duration.ofDays(1L)));

    private ActorSystem actorSystem;

    public void createActorSystem(final Config config) {
        actorSystem = ActorSystem.create(getClass().getSimpleName(), config.withFallback(DISPATCHER_CONFIG));
    }

    @After
    public void shutdownActorSystem() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void loadProvider() {
        createActorSystem(CONFIG);
        final ConnectivitySignalEnrichmentProvider underTest = ConnectivitySignalEnrichmentProvider.get(actorSystem);
        assertThat(underTest).isInstanceOf(ConnectivityCachingSignalEnrichmentProvider.class);
    }

    @Test
    public void loadProviderWithIncorrectConfig() {
        createActorSystem(CONFIG.withValue("ditto.connectivity.signal-enrichment.provider-config.ask-timeout",
                ConfigValueFactory.fromAnyRef("This is not a duration")));
        assertThatExceptionOfType(ConfigException.class)
                .isThrownBy(() -> ConnectivitySignalEnrichmentProvider.get(actorSystem));
    }

}
