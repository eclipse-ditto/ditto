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
package org.eclipse.ditto.timeseries.service.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.timeseries.api.HealthStatus;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapter;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;
import org.eclipse.ditto.timeseries.mongodb.MongoDbTimeseriesAdapter;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class TimeseriesAdapterExtensionTest {

    @Test
    public void mongoDbIsTheConfiguredDefault() {
        final Config serviceConfig = ConfigFactory.parseResources("timeseries.conf");

        assertThat(serviceConfig.getString("ditto.extensions.timeseries-adapter"))
                .isEqualTo(MongoDbTimeseriesAdapter.class.getName());
    }

    @Test
    public void loadsTheMongoDbAdapterFromTheServiceConfig() {
        final Config config = ConfigFactory.parseString(
                        "ditto.mongodb.uri = \"mongodb://localhost:27017/ts\"\n" +
                                "ditto.timeseries.adapter.mongodb.retention-overrides { \"org.eclipse.ditto\" = 7d }")
                .withFallback(ConfigFactory.parseResources("timeseries.conf").resolve().withOnlyPath("ditto"));
        final ActorSystem system = ActorSystem.create("TimeseriesAdapterExtensionTest", config);
        try {
            assertThat(TimeseriesRootActor.loadAdapter(system, system.settings().config()))
                    .isInstanceOf(MongoDbTimeseriesAdapter.class);
        } finally {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void loadsTheConfiguredAdapter() {
        final Config config = ConfigFactory.parseString(
                "ditto.extensions.timeseries-adapter = \"" + CustomAdapter.class.getName() + "\"");
        final ActorSystem system = ActorSystem.create("TimeseriesAdapterExtensionTest", config);
        try {
            assertThat(TimeseriesAdapter.get(system, ScopedConfig.dittoExtension(system.settings().config())))
                    .isInstanceOf(CustomAdapter.class);
        } finally {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void unknownAdapterClassFailsTheStartUp() {
        final Config config = ConfigFactory.parseString(
                "ditto.extensions.timeseries-adapter = \"org.example.NoSuchAdapter\"");
        final ActorSystem system = ActorSystem.create("TimeseriesAdapterExtensionTest", config);
        try {
            assertThatThrownBy(() -> TimeseriesRootActor.loadAdapter(system, system.settings().config()))
                    .isInstanceOf(DittoConfigError.class)
                    .hasMessageContaining("org.example.NoSuchAdapter");
        } finally {
            TestKit.shutdownActorSystem(system);
        }
    }

    public static final class CustomAdapter implements TimeseriesAdapter {

        @SuppressWarnings("unused")
        public CustomAdapter(final ActorSystem actorSystem, final Config config) {
        }

        @Override
        public CompletionStage<Void> initialize() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> shutdown() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public HealthStatus getHealth() {
            return HealthStatus.UP;
        }

        @Override
        public CompletionStage<Void> write(final TimeseriesDataPoint dataPoint) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<List<TimeseriesQueryResult>> query(final TimeseriesQuery query) {
            return CompletableFuture.completedFuture(List.of());
        }

    }

}
