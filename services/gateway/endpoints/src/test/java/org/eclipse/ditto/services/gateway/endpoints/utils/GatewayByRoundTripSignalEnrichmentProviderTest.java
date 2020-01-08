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
package org.eclipse.ditto.services.gateway.endpoints.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.services.base.config.DefaultSignalEnrichmentConfig;
import org.eclipse.ditto.services.base.config.SignalEnrichmentConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link GatewayByRoundTripSignalEnrichmentProvider}.
 */
public final class GatewayByRoundTripSignalEnrichmentProviderTest {

    private ActorSystem actorSystem;
    private SignalEnrichmentConfig signalEnrichmentConfig;

    @Before
    public void createActorSystem() {
        signalEnrichmentConfig =
                DefaultSignalEnrichmentConfig.of(ConfigFactory.load("gateway-by-round-trip-provider-test"));
        actorSystem = ActorSystem.create(getClass().getSimpleName());
    }

    @After
    public void shutdownActorSystem() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void loadProvider() {
        new TestKit(actorSystem) {{
            final GatewaySignalEnrichmentProvider underTest =
                    GatewaySignalEnrichmentProvider.load(actorSystem, getRef(), signalEnrichmentConfig);
            assertThat(underTest).isInstanceOf(GatewayByRoundTripSignalEnrichmentProvider.class);
        }};
    }

    @Test
    public void loadProviderWithNonexistentClass() {
        new TestKit(actorSystem) {{
            final SignalEnrichmentConfig badConfig = DefaultSignalEnrichmentConfig.of(signalEnrichmentConfig.render()
                    .withValue("signal-enrichment.provider",
                            ConfigValueFactory.fromAnyRef(getClass().getCanonicalName() + "_NonexistentClass")));
            assertThatExceptionOfType(ClassNotFoundException.class)
                    .isThrownBy(() -> GatewaySignalEnrichmentProvider.load(actorSystem, getRef(), badConfig))
                    .withMessageContaining("_NonexistentClass");
        }};
    }

    @Test
    public void loadProviderWithIncorrectClass() {
        new TestKit(actorSystem) {{
            final SignalEnrichmentConfig badConfig = DefaultSignalEnrichmentConfig.of(signalEnrichmentConfig.render()
                    .withValue("signal-enrichment.provider",
                            ConfigValueFactory.fromAnyRef("java.lang.Object")));
            assertThatExceptionOfType(ClassCastException.class)
                    .isThrownBy(() -> GatewaySignalEnrichmentProvider.load(actorSystem, getRef(), badConfig));
        }};
    }

    @Test
    public void loadProviderWithIncorrectConfig() {
        new TestKit(actorSystem) {{
            final SignalEnrichmentConfig badConfig = DefaultSignalEnrichmentConfig.of(signalEnrichmentConfig.render()
                    .withValue("signal-enrichment.provider-config.ask-timeout",
                            ConfigValueFactory.fromAnyRef("This is not a duration")));
            assertThatExceptionOfType(ConfigException.class)
                    .isThrownBy(() -> GatewaySignalEnrichmentProvider.load(actorSystem, getRef(), badConfig));
        }};
    }

}