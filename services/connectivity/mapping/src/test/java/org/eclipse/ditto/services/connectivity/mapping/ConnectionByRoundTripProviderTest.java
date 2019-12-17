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

import org.junit.After;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link org.eclipse.ditto.services.connectivity.mapping.ConnectionByRoundTripProvider}.
 */
public final class ConnectionByRoundTripProviderTest {

    private final Config config = ConfigFactory.empty()
            .withValue("ditto.connectivity.connection-enrichment.provider",
                    ConfigValueFactory.fromAnyRef(ConnectionByRoundTripProvider.class.getCanonicalName()))
            .withValue("ditto.connectivity.connection-enrichment.config.ask-timeout",
                    ConfigValueFactory.fromAnyRef(Duration.ofDays(1L)));
    ;

    private ActorSystem actorSystem;

    private ActorSystem createActorSystem(final Config config) {
        shutdownActorSystem();
        actorSystem = ActorSystem.create(getClass().getSimpleName(), config);
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
        new TestKit(createActorSystem(config)) {{
            final ConnectionEnrichmentProvider underTest = ConnectionEnrichmentProvider.load(actorSystem, getRef());
            assertThat(underTest).isInstanceOf(ConnectionByRoundTripProvider.class);
        }};
    }

    @Test
    public void loadProviderWithNonexistentClass() {
        final Config badConfig = config.withValue("ditto.connectivity.connection-enrichment.provider",
                ConfigValueFactory.fromAnyRef(getClass().getCanonicalName() + "_NonexistentClass"));
        new TestKit(createActorSystem(badConfig)) {{
            assertThatExceptionOfType(ClassNotFoundException.class)
                    .isThrownBy(() -> ConnectionEnrichmentProvider.load(actorSystem, getRef()));
        }};
    }

    @Test
    public void loadProviderWithIncorrectClass() {
        final Config badConfig = config.withValue("ditto.connectivity.connection-enrichment.provider",
                ConfigValueFactory.fromAnyRef("java.lang.Object"));
        new TestKit(createActorSystem(badConfig)) {{
            assertThatExceptionOfType(ClassCastException.class)
                    .isThrownBy(() -> ConnectionEnrichmentProvider.load(actorSystem, getRef()));
        }};
    }

    @Test
    public void loadProviderWithIncorrectConfig() {
        final Config badConfig = config.withValue("ditto.connectivity.connection-enrichment.config.ask-timeout",
                ConfigValueFactory.fromAnyRef("This is not a duration"));
        new TestKit(createActorSystem(badConfig)) {{
            assertThatExceptionOfType(ConfigException.class)
                    .isThrownBy(() -> ConnectionEnrichmentProvider.load(actorSystem, getRef()));
        }};
    }
}
