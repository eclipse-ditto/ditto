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
 * Tests {@link GatewayCachingSignalEnrichmentProvider}.
 *
 * TODO TJ add test for WeakReference subscription
 */
public final class GatewayCachingSignalEnrichmentProviderTest {

    private ActorSystem actorSystem;
    private SignalEnrichmentConfig signalEnrichmentConfig;

    @Before
    public void createActorSystem() {
        signalEnrichmentConfig =
                DefaultSignalEnrichmentConfig.of(ConfigFactory.load("gateway-caching-provider-test"));
        actorSystem =
                ActorSystem.create(getClass().getSimpleName(), ConfigFactory.load("gateway-caching-provider-test"));
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
                    GatewaySignalEnrichmentProvider.load(actorSystem, getRef(), getRef(), signalEnrichmentConfig);
            assertThat(underTest).isInstanceOf(GatewayCachingSignalEnrichmentProvider.class);
        }};
    }

    @Test
    public void loadProviderWithIncorrectConfig() {
        new TestKit(actorSystem) {{
            final SignalEnrichmentConfig badConfig = DefaultSignalEnrichmentConfig.of(signalEnrichmentConfig.render()
                    .withValue("signal-enrichment.provider-config.ask-timeout",
                            ConfigValueFactory.fromAnyRef("This is not a duration")));
            assertThatExceptionOfType(ConfigException.class)
                    .isThrownBy(() -> GatewaySignalEnrichmentProvider.load(actorSystem, getRef(), getRef(), badConfig));
        }};
    }

}