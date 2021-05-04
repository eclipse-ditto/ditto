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

import org.junit.After;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link ConnectivityByRoundTripSignalEnrichmentProvider}.
 */
public final class ConnectivityByRoundTripSignalEnrichmentProviderTest {

    private static final Config CONFIG = ConfigFactory.empty()
            .withValue("ditto.connectivity.signal-enrichment.provider",
                    ConfigValueFactory.fromAnyRef(
                            ConnectivityByRoundTripSignalEnrichmentProvider.class.getCanonicalName()))
            .withValue("ditto.connectivity.signal-enrichment.provider-config.ask-timeout",
                    ConfigValueFactory.fromAnyRef(Duration.ofDays(1L)));

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
        final ConnectivitySignalEnrichmentProvider underTest = ConnectivitySignalEnrichmentProvider.get(actorSystem);
        assertThat(underTest).isInstanceOf(ConnectivityByRoundTripSignalEnrichmentProvider.class);
    }

    @Test
    public void loadProviderWithNonexistentClass() {
        createActorSystem(withValue("ditto.connectivity.signal-enrichment.provider",
                getClass().getCanonicalName() + "_NonexistentClass"));
        assertThatExceptionOfType(ClassNotFoundException.class)
                .isThrownBy(() -> ConnectivitySignalEnrichmentProvider.get(actorSystem));
    }

    @Test
    public void loadProviderWithIncorrectClass() {
        createActorSystem(withValue("ditto.connectivity.signal-enrichment.provider", "java.lang.Object"));
        assertThatExceptionOfType(ClassCastException.class)
                .isThrownBy(() -> ConnectivitySignalEnrichmentProvider.get(actorSystem));
    }

    @Test
    public void loadProviderWithIncorrectConfig() {
        createActorSystem(withValue("ditto.connectivity.signal-enrichment.provider-config.ask-timeout",
                "This is not a duration"));
        assertThatExceptionOfType(ConfigException.class)
                .isThrownBy(() -> ConnectivitySignalEnrichmentProvider.get(actorSystem));
    }

    private Config withValue(final String key, final String value) {
        return CONFIG.withValue(key, ConfigValueFactory.fromAnyRef(value));
    }
}
