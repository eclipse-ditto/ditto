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
package org.eclipse.ditto.connectivity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.cluster.sharding.ClusterShardingSettings;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.connectivity.api.ConnectivityMessagingConstants;
import org.eclipse.ditto.internal.utils.config.raw.RawConfigSupplier;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendProvider;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoPersistenceBackendProvider;
import org.eclipse.ditto.internal.utils.persistence.postgres.PostgresPersistenceBackendProvider;
import org.junit.After;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Verifies the remember-entities store plugin IDs of the connection shard region's {@link ClusterShardingSettings}
 * built by {@link ConnectivityRootActor}, on the shipped connectivity configuration:
 * <ul>
 *     <li>on MongoDB the plugin config names no remember-store plugins, so the settings carry the
 *     {@code pekko.cluster.sharding.journal-plugin-id} / {@code snapshot-plugin-id} of the configuration, both the
 *     shipped {@code pekko-contrib-mongodb-persistence-connection-remember-*} ones and an operator's override;</li>
 *     <li>on PostgreSQL the plugin config names the dedicated {@code ditto-postgres-connection-remember-*} plugins,
 *     which the settings carry whatever the configuration says.</li>
 * </ul>
 */
public final class ConnectivityRootActorShardingSettingsTest {

    private static final String OVERRIDDEN_PLUGIN_IDS = """
            pekko.cluster.sharding.journal-plugin-id = "custom-journal"
            pekko.cluster.sharding.snapshot-plugin-id = "custom-snapshots"
            """;

    private final List<ActorSystem> actorSystems = new ArrayList<>();

    @After
    public void tearDown() {
        actorSystems.forEach(TestKit::shutdownActorSystem);
        actorSystems.clear();
    }

    @Test
    public void mongoDbKeepsThePluginIdsOfTheConfiguration() {
        final ActorSystem system = newSystem("");
        final PersistenceBackendProvider provider = new MongoPersistenceBackendProvider(system, ConfigFactory.empty());

        final ClusterShardingSettings shardingSettings = shardingSettings(system, provider);

        assertThat(shardingSettings.journalPluginId())
                .isEqualTo("pekko-contrib-mongodb-persistence-connection-remember-journal");
        assertThat(shardingSettings.snapshotPluginId())
                .isEqualTo("pekko-contrib-mongodb-persistence-connection-remember-snapshots");
        assertConnectivityRole(shardingSettings);
    }

    @Test
    public void mongoDbHonoursOverriddenPluginIds() {
        final ActorSystem system = newSystem(OVERRIDDEN_PLUGIN_IDS);
        final PersistenceBackendProvider provider = new MongoPersistenceBackendProvider(system, ConfigFactory.empty());

        final ClusterShardingSettings shardingSettings = shardingSettings(system, provider);

        assertThat(shardingSettings.journalPluginId()).isEqualTo("custom-journal");
        assertThat(shardingSettings.snapshotPluginId()).isEqualTo("custom-snapshots");
        assertConnectivityRole(shardingSettings);
    }

    @Test
    public void postgresSetsItsPluginIdsWhateverTheConfigurationSays() {
        final ActorSystem system = newSystem(OVERRIDDEN_PLUGIN_IDS + "ditto.postgresql.ssl.mode = \"disable\"\n");
        final PersistenceBackendProvider provider = new PostgresPersistenceBackendProvider(system,
                ConfigFactory.parseResources("ditto-postgres-persistence.conf")
                        .getConfig("ditto.extensions.persistence-backend-provider.extension-config"));

        final ClusterShardingSettings shardingSettings = shardingSettings(system, provider);

        assertThat(shardingSettings.journalPluginId()).isEqualTo("ditto-postgres-connection-remember-journal");
        assertThat(shardingSettings.snapshotPluginId()).isEqualTo("ditto-postgres-connection-remember-snapshots");
        assertConnectivityRole(shardingSettings);
    }

    private ActorSystem newSystem(final String overrideHocon) {
        final ActorSystem system = ActorSystem.create(getClass().getSimpleName(),
                ConfigFactory.parseString(overrideHocon)
                        .withFallback(ConfigFactory.parseMap(Map.of(
                                "pekko.remote.artery.canonical.port", 0,
                                "pekko.cluster.seed-nodes", List.of(),
                                "pekko.coordinated-shutdown.exit-jvm", "off")))
                        .withFallback(RawConfigSupplier.of("connectivity").get()));
        actorSystems.add(system);
        return system;
    }

    private static ClusterShardingSettings shardingSettings(final ActorSystem system,
            final PersistenceBackendProvider provider) {

        return ConnectivityRootActor.rememberEntitiesShardingSettings(system,
                provider.pluginConfig().getRememberStorePluginIds(ConnectivityMessagingConstants.SHARD_REGION)
                        .orElse(null));
    }

    private static void assertConnectivityRole(final ClusterShardingSettings shardingSettings) {
        assertThat(shardingSettings.role().isDefined()).as("connection shard region has a cluster role").isTrue();
        assertThat(shardingSettings.role().get()).isEqualTo(ConnectivityMessagingConstants.CLUSTER_ROLE);
    }
}
