package org.eclipse.ditto.services.utils.protocol;/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;

/**
 * Unit test of {@link ProtocolConfigReader}.
 */
public final class ProtocolConfigReaderTest {

    @Test
    public void testDefaultConfiguration() {
        final Config rawConfig = defaultRawConfig();
        final ProtocolConfigReader underTest = ProtocolConfigReader.fromRawConfig(rawConfig);

        assertThat(underTest.blacklist()).contains("connection", "cache-control", "authorization", "raw-request-uri");
        assertThat(underTest.incompatibleBlacklist()).contains("thing-id", "feature-id");
    }

    @Test
    public void testDynamicProtocolAdapterLoading() {
        final Config rawConfig = defaultRawConfig();
        final ProtocolConfigReader underTest = ProtocolConfigReader.fromRawConfig(rawConfig);
        final ActorSystem actorSystem = ActorSystem.create();

        final ProtocolAdapterProvider provider = underTest.loadProtocolAdapterProvider(actorSystem);
        assertThat(provider.getClass()).isEqualTo(DittoProtocolAdapterProvider.class);
        final ProtocolAdapter protocolAdapter1 = provider.getProtocolAdapter("bumlux");
        assertThat(protocolAdapter1.getClass()).isEqualTo(DittoProtocolAdapter.class);
        // make sure that userAgent param may be null
        final ProtocolAdapter protocolAdapter2 = provider.getProtocolAdapter(null);
        assertThat(protocolAdapter2.getClass()).isEqualTo(DittoProtocolAdapter.class);
        // performance: make sure that the same instance is returned for each request
        assertThat(protocolAdapter1).isSameAs(protocolAdapter2);
    }

    private static Config defaultRawConfig() {
        return ConfigFactory.load(ProtocolConfigReader.class.getClassLoader(), "ditto-service-base");
    }
}
