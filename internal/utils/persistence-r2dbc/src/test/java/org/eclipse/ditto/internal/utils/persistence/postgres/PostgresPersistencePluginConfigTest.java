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
package org.eclipse.ditto.internal.utils.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import java.util.List;

import org.eclipse.ditto.internal.utils.persistence.api.PersistencePluginConfig;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link PostgresPersistencePluginConfig} (task E2, mirroring the Mongo B1 test): per-entity IDs delegate
 * to the provider, the per-service auto-start IDs match the shipped Postgres profile, and the remember-store IDs name
 * the profile's {@code connection-remember} plugin blocks. Runs offline against the real shipped
 * {@code ditto-postgres-persistence.conf}.
 */
public final class PostgresPersistencePluginConfigTest {

    private static final Config PROFILE_EXTENSION_CONFIG =
            ConfigFactory.parseResources("ditto-postgres-persistence.conf")
                    .getConfig("ditto.extensions.persistence-backend-provider.extension-config");

    private static ActorSystem system;
    private static PersistencePluginConfig pluginConfig;

    @BeforeClass
    public static void setUp() {
        system = ActorSystem.create("PostgresPersistencePluginConfigTest",
                ConfigFactory.parseString("ditto.postgresql.ssl.mode = \"disable\"")
                        .withFallback(ConfigFactory.load()));
        final PostgresPersistenceBackendProvider provider =
                new PostgresPersistenceBackendProvider(system, PROFILE_EXTENSION_CONFIG);
        pluginConfig = provider.pluginConfig();
    }

    @AfterClass
    public static void tearDown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    @Test
    public void perEntityIdsDelegateToProvider() {
        assertThat(pluginConfig.getJournalPluginId("thing")).isEqualTo("ditto-postgres-things-journal");
        assertThat(pluginConfig.getSnapshotPluginId("thing")).isEqualTo("ditto-postgres-things-snapshots");
        assertThat(pluginConfig.getJournalPluginId("connection")).isEqualTo("ditto-postgres-connections-journal");
        assertThat(pluginConfig.getSnapshotPluginId("connection")).isEqualTo("ditto-postgres-connections-snapshots");
    }

    @Test
    public void autoStartIdsMatchShippedProfilePerService() {
        assertThat(pluginConfig.getAutoStartPluginIds("things"))
                .containsExactly("ditto-postgres-things-journal", "ditto-postgres-things-snapshots");
        assertThat(pluginConfig.getAutoStartPluginIds("policies"))
                .containsExactly("ditto-postgres-policies-journal", "ditto-postgres-policies-snapshots");
        assertThat(pluginConfig.getAutoStartPluginIds("connectivity"))
                .containsExactly("ditto-postgres-connections-journal", "ditto-postgres-connections-snapshots");
    }

    @Test
    public void autoStartIdsForUnknownServiceIsEmpty() {
        assertThat(pluginConfig.getAutoStartPluginIds("gateway")).isEmpty();
    }

    @Test
    public void rememberStoreIdsForConnectionNameTheDedicatedRememberPlugins() {
        // Like MongoDB's connection-remember collections: the remember-entities store gets plugins and tables of its
        // own, so the persistence ping and cleanup never see Pekko's /sharding/... persistence ids.
        final PersistencePluginConfig.RememberStorePluginIds ids =
                pluginConfig.getRememberStorePluginIds("connection");

        assertThat(ids.journalPluginId()).isEqualTo("ditto-postgres-connection-remember-journal");
        assertThat(ids.snapshotPluginId()).isEqualTo("ditto-postgres-connection-remember-snapshots");
    }

    @Test
    public void rememberStoreIdsResolveToBlocksOfTheShippedProfileAndAreNotAutoStarted() {
        // what the profile's blocks look like is PostgresProfileHoconLintTest's business; here: the IDs this config
        // hands to the shard region exist in the profile, and connectivity does not auto-start them (as on MongoDB)
        final Config profile = ConfigFactory.parseResources("ditto-postgres-persistence.conf").resolve();
        final PersistencePluginConfig.RememberStorePluginIds ids =
                pluginConfig.getRememberStorePluginIds("connection");

        for (final String block : List.of(ids.journalPluginId(), ids.snapshotPluginId())) {
            assertThat(profile.hasPath(block + ".class")).as(block + " is a plugin block of the profile").isTrue();
        }
        assertThat(pluginConfig.getAutoStartPluginIds("connectivity"))
                .doesNotContain(ids.journalPluginId(), ids.snapshotPluginId());
    }

    @Test
    public void rememberStoreIdsForUnknownEntityFailsClearly() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> pluginConfig.getRememberStorePluginIds("thing"))
                .withMessageContaining("thing");
    }
}
