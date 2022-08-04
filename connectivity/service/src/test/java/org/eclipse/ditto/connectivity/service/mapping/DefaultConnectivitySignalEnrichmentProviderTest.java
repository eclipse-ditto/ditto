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
package org.eclipse.ditto.connectivity.service.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;

import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.internal.models.signalenrichment.ByRoundTripSignalEnrichmentFacade;
import org.eclipse.ditto.internal.models.signalenrichment.DittoCachingSignalEnrichmentFacade;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.junit.After;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link DefaultConnectivitySignalEnrichmentProvider}.
 */
public final class DefaultConnectivitySignalEnrichmentProviderTest {

    private static final Config CONFIG = ConfigFactory.empty()
            .withValue("ditto.extensions.signal-enrichment-provider.extension-class",
                    ConfigValueFactory.fromAnyRef(DefaultConnectivitySignalEnrichmentProvider.class.getCanonicalName()))
            .withValue("ditto.extensions.signal-enrichment-provider.extension-config.ask-timeout",
                    ConfigValueFactory.fromAnyRef(Duration.ofDays(1L)))
            .withValue("ditto.extensions.signal-enrichment-provider.extension-config.cache.enabled",
                    ConfigValueFactory.fromAnyRef(false));

    private ActorSystem actorSystem;

    private void createActorSystem(final Config config) {
        shutdownActorSystem();
        actorSystem = ActorSystem.create(getClass().getSimpleName(), config);
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
        final var dittoExtensionsConfig = ScopedConfig.dittoExtension(actorSystem.settings().config());
        final var underTest = ConnectivitySignalEnrichmentProvider.get(actorSystem, dittoExtensionsConfig);
        assertThat(underTest).isInstanceOf(DefaultConnectivitySignalEnrichmentProvider.class);
    }

    @Test
    public void withDisabledCaching() {
        createActorSystem(CONFIG);
        final var dittoExtensionsConfig = ScopedConfig.dittoExtension(actorSystem.settings().config());
        final var underTest = ConnectivitySignalEnrichmentProvider.get(actorSystem, dittoExtensionsConfig);
        final SignalEnrichmentFacade facade = underTest.getFacade(ConnectionId.generateRandom());
        assertThat(facade).isInstanceOf(ByRoundTripSignalEnrichmentFacade.class);
    }

    @Test
    public void withEnabledCaching() {
        final Config dispatcherConfig = ConfigFactory.parseString("signal-enrichment-cache-dispatcher {\n" +
                "  type = Dispatcher\n" +
                "  executor = \"fork-join-executor\"\n" +
                "  fork-join-executor {\n" +
                "    parallelism-min = 4\n" +
                "    parallelism-factor = 3.0\n" +
                "    parallelism-max = 32\n" +
                "  }\n" +
                "  throughput = 5\n" +
                "}");
        createActorSystem(
                withValue("ditto.extensions.signal-enrichment-provider.extension-config.cache.enabled", "true")
                        .withFallback(dispatcherConfig));
        final var dittoExtensionsConfig = ScopedConfig.dittoExtension(actorSystem.settings().config());
        final var underTest = ConnectivitySignalEnrichmentProvider.get(actorSystem, dittoExtensionsConfig);
        final SignalEnrichmentFacade facade = underTest.getFacade(ConnectionId.generateRandom());
        assertThat(facade).isInstanceOf(DittoCachingSignalEnrichmentFacade.class);
    }

    @Test
    public void loadProviderWithNonexistentClass() {
        createActorSystem(withValue("ditto.extensions.signal-enrichment-provider",
                getClass().getCanonicalName() + "_NonexistentClass"));
        final var dittoExtensionsConfig = ScopedConfig.dittoExtension(actorSystem.settings().config());
        assertThatExceptionOfType(ClassNotFoundException.class)
                .isThrownBy(() -> ConnectivitySignalEnrichmentProvider.get(actorSystem, dittoExtensionsConfig));
    }

    @Test
    public void loadProviderWithIncorrectClass() {
        createActorSystem(withValue("ditto.extensions.signal-enrichment-provider", "java.lang.Object"));
        final var dittoExtensionsConfig = ScopedConfig.dittoExtension(actorSystem.settings().config());
        assertThatExceptionOfType(ClassCastException.class)
                .isThrownBy(() -> ConnectivitySignalEnrichmentProvider.get(actorSystem, dittoExtensionsConfig));
    }

    @Test
    public void loadProviderWithIncorrectConfig() {
        createActorSystem(withValue("ditto.extensions.signal-enrichment-provider.extension-config.ask-timeout",
                "This is not a duration"));
        final var dittoExtensionsConfig = ScopedConfig.dittoExtension(actorSystem.settings().config());
        assertThatExceptionOfType(ConfigException.class)
                .isThrownBy(() -> ConnectivitySignalEnrichmentProvider.get(actorSystem, dittoExtensionsConfig));
    }

    private Config withValue(final String key, final String value) {
        return CONFIG.withValue(key, ConfigValueFactory.fromAnyRef(value));
    }
}
