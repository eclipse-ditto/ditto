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
package org.eclipse.ditto.internal.utils.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.internal.utils.protocol.config.DefaultProtocolConfig;
import org.eclipse.ditto.internal.utils.protocol.config.ProtocolConfig;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;

/**
 * Unit test for {@link ProtocolAdapterProvider}.
 */
public final class ProtocolAdapterProviderTest {

    private static ProtocolConfig protocolConfig;
    private static ActorSystem actorSystem;

    @BeforeClass
    public static void initTestFixture() {
        final Config dittoServiceBaseConfig =
                ConfigFactory.load(ProtocolAdapterProvider.class.getClassLoader(), "ditto-service-base");
        protocolConfig = DefaultProtocolConfig.of(dittoServiceBaseConfig.getConfig("ditto"));

        actorSystem = ActorSystem.create();
    }

    @Test
    public void loadConfiguredProtocolAdapterProvider() {
        final ProtocolAdapterProvider adapterProvider = ProtocolAdapterProvider.load(protocolConfig, actorSystem);

        assertThat(adapterProvider).isInstanceOf(DittoProtocolAdapterProvider.class);
        assertThat(adapterProvider.getProtocolAdapter("bumlux")).isInstanceOf(DittoProtocolAdapter.class);
        assertThat(adapterProvider.getProtocolAdapter(null)).isInstanceOf(DittoProtocolAdapter.class);
    }

}
