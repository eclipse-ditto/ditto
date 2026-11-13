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

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.cluster.sharding.ClusterShardingSettings;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.connectivity.api.ConnectivityMessagingConstants;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendProvider;
import org.eclipse.ditto.internal.utils.persistence.api.PersistencePluginConfig.RememberStorePluginIds;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoPersistenceBackendProvider;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Verifies that {@link ConnectivityRootActor} builds the connection shard-region {@link ClusterShardingSettings}
 * with the cluster-sharding {@code remember-entities} store's journal/snapshot plugin IDs sourced from the active
 * persistence backend's provider rather than relying solely on HOCON.
 * <p>
 * On the default (Mongo) backend the provider returns the dedicated {@code *-remember-*} plugin IDs, so the resulting
 * sharding settings must carry exactly those — keeping the effective Mongo remember-entities behaviour identical.
 */
public final class ConnectivityRootActorShardingSettingsTest {

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void setUp() {
        // A plain local actor system is enough: ClusterShardingSettings.create reads its defaults from reference.conf,
        // and the test only asserts the programmatic remember-store plugin-id overrides — no clustering/remoting needed.
        actorSystem = ActorSystem.create("ConnectivityRootActorShardingSettingsTest");
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void shardingSettingsCarryTheProvidersRememberStorePluginIds() {
        final RememberStorePluginIds providerIds =
                RememberStorePluginIds.of("custom-journal-plugin-id", "custom-snapshot-plugin-id");

        final ClusterShardingSettings shardingSettings =
                ConnectivityRootActor.rememberEntitiesShardingSettings(actorSystem, providerIds);

        assertThat(shardingSettings.journalPluginId())
                .as("remember-store journal plugin ID must come from the provider")
                .isEqualTo(providerIds.journalPluginId());
        assertThat(shardingSettings.snapshotPluginId())
                .as("remember-store snapshot plugin ID must come from the provider")
                .isEqualTo(providerIds.snapshotPluginId());
        assertThat(shardingSettings.role().isDefined())
                .as("connection shard region keeps a cluster role")
                .isTrue();
        assertThat(shardingSettings.role().get())
                .as("connection shard region keeps the connectivity cluster role")
                .isEqualTo(ConnectivityMessagingConstants.CLUSTER_ROLE);
    }

    @Test
    public void mongoBackendKeepsTheDedicatedRememberPluginIds() {
        final PersistenceBackendProvider mongoProvider =
                new MongoPersistenceBackendProvider(actorSystem, ConfigFactory.empty());
        final RememberStorePluginIds mongoIds =
                mongoProvider.pluginConfig().getRememberStorePluginIds(ConnectivityMessagingConstants.SHARD_REGION);

        // behaviour-identical assertion: these are exactly the *-remember-* IDs that connectivity.conf:1175-1176 ship.
        assertThat(mongoIds.journalPluginId())
                .isEqualTo("pekko-contrib-mongodb-persistence-connection-remember-journal");
        assertThat(mongoIds.snapshotPluginId())
                .isEqualTo("pekko-contrib-mongodb-persistence-connection-remember-snapshots");

        final ClusterShardingSettings shardingSettings =
                ConnectivityRootActor.rememberEntitiesShardingSettings(actorSystem, mongoIds);

        assertThat(shardingSettings.journalPluginId()).isEqualTo(mongoIds.journalPluginId());
        assertThat(shardingSettings.snapshotPluginId()).isEqualTo(mongoIds.snapshotPluginId());
    }
}
