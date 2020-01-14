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
package org.eclipse.ditto.services.connectivity.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;

import org.eclipse.ditto.services.base.config.DefaultSignalEnrichmentConfig;
import org.eclipse.ditto.services.base.config.SignalEnrichmentConfig;
import org.junit.After;
import org.junit.Test;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link ConnectivityByRoundTripSignalEnrichmentProvider}.
 */
public final class ConnectivityByRoundTripSignalEnrichmentProviderTest {

    private final SignalEnrichmentConfig config = DefaultSignalEnrichmentConfig.of(ConfigFactory.empty()
            .withValue("signal-enrichment.provider",
                    ConfigValueFactory.fromAnyRef(ConnectivityByRoundTripSignalEnrichmentProvider.class.getCanonicalName()))
            .withValue("signal-enrichment.provider-config.ask-timeout",
                    ConfigValueFactory.fromAnyRef(Duration.ofDays(1L))));

    private ActorSystem actorSystem;

    private ActorSystem createActorSystem() {
        shutdownActorSystem();
        actorSystem = ActorSystem.create(getClass().getSimpleName());
        return actorSystem;
    }

    @After
    public void shutdownActorSystem() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void loadProvider() {
        new TestKit(createActorSystem()) {{
            final ConnectivitySignalEnrichmentProvider
                    underTest = ConnectivitySignalEnrichmentProvider.load(actorSystem, getRef(), config);
            assertThat(underTest).isInstanceOf(ConnectivityByRoundTripSignalEnrichmentProvider.class);
        }};
    }

    @Test
    public void loadProviderWithNonexistentClass() {
        final SignalEnrichmentConfig badConfig =
                withValue("signal-enrichment.provider", getClass().getCanonicalName() + "_NonexistentClass");
        new TestKit(createActorSystem()) {{
            assertThatExceptionOfType(ClassNotFoundException.class)
                    .isThrownBy(() -> ConnectivitySignalEnrichmentProvider.load(actorSystem, getRef(), badConfig));
        }};
    }

    @Test
    public void loadProviderWithIncorrectClass() {
        final SignalEnrichmentConfig badConfig = withValue("signal-enrichment.provider", "java.lang.Object");
        new TestKit(createActorSystem()) {{
            assertThatExceptionOfType(ClassCastException.class)
                    .isThrownBy(() -> ConnectivitySignalEnrichmentProvider.load(actorSystem, getRef(), badConfig));
        }};
    }

    @Test
    public void loadProviderWithIncorrectConfig() {
        final SignalEnrichmentConfig badConfig =
                withValue("signal-enrichment.provider-config.ask-timeout", "This is not a duration");
        new TestKit(createActorSystem()) {{
            assertThatExceptionOfType(ConfigException.class)
                    .isThrownBy(() -> ConnectivitySignalEnrichmentProvider.load(actorSystem, getRef(), badConfig));
        }};
    }

    private DefaultSignalEnrichmentConfig withValue(final String key, final String value) {
        return DefaultSignalEnrichmentConfig.of(config.render().withValue(key, ConfigValueFactory.fromAnyRef(value)));
    }
}
