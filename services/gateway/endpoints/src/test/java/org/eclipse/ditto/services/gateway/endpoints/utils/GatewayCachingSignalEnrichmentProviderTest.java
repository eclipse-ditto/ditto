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

import org.junit.After;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link GatewayCachingSignalEnrichmentProvider}.
 */
public final class GatewayCachingSignalEnrichmentProviderTest {

    private static final Config CONFIG = ConfigFactory.load("gateway-caching-provider-test");

    private ActorSystem actorSystem;

    @After
    public void shutdownActorSystem() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void loadProvider() {
        actorSystem = ActorSystem.create("loadCachingProvider", CONFIG);
        final GatewaySignalEnrichmentProvider underTest = GatewaySignalEnrichmentProvider.get(actorSystem);
        assertThat(underTest).isInstanceOf(GatewayCachingSignalEnrichmentProvider.class);
    }

    @Test
    public void loadProviderWithIncorrectConfig() {
        final ConfigValue string = ConfigValueFactory.fromAnyRef("This is not a duration");
        final Config badConfig =
                CONFIG.withValue("ditto.gateway.streaming.signal-enrichment.provider-config.ask-timeout", string);
        actorSystem = ActorSystem.create("loadCachingProviderWithIncorrectConfig", badConfig);
        assertThatExceptionOfType(ConfigException.class)
                .isThrownBy(() -> GatewaySignalEnrichmentProvider.get(actorSystem));
    }

}