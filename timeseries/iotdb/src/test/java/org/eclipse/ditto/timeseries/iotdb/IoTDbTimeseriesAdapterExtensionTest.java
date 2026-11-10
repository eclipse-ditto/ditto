/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.timeseries.iotdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapter;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class IoTDbTimeseriesAdapterExtensionTest {

    @Test
    public void includingTheExtensionConfigSelectsAndConfiguresTheIoTDbAdapter() throws Exception {
        final Config config = ConfigFactory.parseString("""
                ditto.extensions.timeseries-adapter = "org.example.DefaultAdapter"
                include required(classpath("ditto-timeseries-iotdb.conf"))
                ditto.timeseries.adapter.iotdb.retention-overrides { "org.eclipse.ditto" = 7d }
                """).resolve();
        final ActorSystem system = ActorSystem.create("IoTDbTimeseriesAdapterExtensionTest", config);
        try {
            assertThat(TimeseriesAdapter.get(system, ScopedConfig.dittoExtension(system.settings().config())))
                    .isInstanceOf(IoTDbTimeseriesAdapter.class);
            final IoTDbTimeseriesAdapterConfig loaded = DefaultIoTDbTimeseriesAdapterConfig.of(
                    system.settings().config().getConfig(IoTDbTimeseriesAdapter.CONFIG_PATH));
            assertThat(loaded.getRetention("org.eclipse.ditto")).contains(Duration.ofDays(7));
            assertThat(loaded.getRetention("com.acme")).contains(Duration.ofDays(90));
        } finally {
            system.terminate();
            system.getWhenTerminated().toCompletableFuture().get();
        }
    }

}
