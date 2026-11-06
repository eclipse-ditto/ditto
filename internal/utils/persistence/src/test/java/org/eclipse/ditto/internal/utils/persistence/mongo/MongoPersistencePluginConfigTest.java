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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.api.PersistencePluginConfig;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit tests for {@link MongoPersistencePluginConfig}.
 * <p>
 * These tests assert the exact shipped Mongo plugin IDs per service (auto-start sets) and the connectivity
 * remember-store IDs, and serve as the "auto-start set == shipped static list" check at the unit level.
 * Expected values are explicit literals that match the shipped HOCON:
 * <ul>
 *     <li>things.conf lines 609–614: things auto-start journal + snapshot (WoT excluded).</li>
 *     <li>policies.conf lines 291–296: policies auto-start journal + snapshot.</li>
 *     <li>connectivity.conf lines 1227–1232: connectivity auto-start journal + snapshot.</li>
 *     <li>connectivity.conf lines 1175–1176: connection remember-store journal + snapshot.</li>
 * </ul>
 *
 * @since 4.0.0
 */
public final class MongoPersistencePluginConfigTest {

    private static ActorSystem system;
    private static Config referenceExtensionConfig;

    private MongoPersistencePluginConfig underTest;

    @BeforeClass
    public static void setUpClass() {
        system = ActorSystem.create("MongoPersistencePluginConfigTest", ConfigFactory.load("reference"));
        referenceExtensionConfig =
                ScopedConfig.dittoExtension(ConfigFactory.load("reference"))
                        .getConfig("persistence-backend-provider.extension-config");
    }

    @AfterClass
    public static void tearDownClass() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    @Before
    public void setUp() {
        final var provider = new MongoPersistenceBackendProvider(system, referenceExtensionConfig);
        underTest = MongoPersistencePluginConfig.of(provider);
    }

    // ── per-entity journal / snapshot IDs ───────────────────────────────────

    @Test
    public void thingJournalPluginIdMatchesShippedBlock() {
        assertThat(underTest.getJournalPluginId("thing"))
                .as("thing journal plugin id must match the shipped pekko block name (things.conf)")
                .isEqualTo("pekko-contrib-mongodb-persistence-things-journal");
    }

    @Test
    public void thingSnapshotPluginIdMatchesShippedBlock() {
        assertThat(underTest.getSnapshotPluginId("thing"))
                .as("thing snapshot plugin id must match the shipped pekko block name (things.conf)")
                .isEqualTo("pekko-contrib-mongodb-persistence-things-snapshots");
    }

    @Test
    public void policyJournalPluginIdMatchesShippedBlock() {
        assertThat(underTest.getJournalPluginId("policy"))
                .as("policy journal plugin id must match the shipped pekko block name (policies.conf)")
                .isEqualTo("pekko-contrib-mongodb-persistence-policies-journal");
    }

    @Test
    public void policySnapshotPluginIdMatchesShippedBlock() {
        assertThat(underTest.getSnapshotPluginId("policy"))
                .as("policy snapshot plugin id must match the shipped pekko block name (policies.conf)")
                .isEqualTo("pekko-contrib-mongodb-persistence-policies-snapshots");
    }

    @Test
    public void connectionJournalPluginIdMatchesShippedBlock() {
        assertThat(underTest.getJournalPluginId("connection"))
                .as("connection journal plugin id must match the shipped pekko block name (connectivity.conf)")
                .isEqualTo("pekko-contrib-mongodb-persistence-connection-journal");
    }

    @Test
    public void connectionSnapshotPluginIdMatchesShippedBlock() {
        assertThat(underTest.getSnapshotPluginId("connection"))
                .as("connection snapshot plugin id must match the shipped pekko block name (connectivity.conf)")
                .isEqualTo("pekko-contrib-mongodb-persistence-connection-snapshots");
    }

    // ── auto-start sets (regression guard) ───────────────────────────────────

    @Test
    public void thingsAutoStartSetMatchesShippedHocon() {
        // things.conf:609-614 — journal + snapshot only; WoT is NOT in this set
        final List<String> expected = List.of(
                "pekko-contrib-mongodb-persistence-things-journal",
                "pekko-contrib-mongodb-persistence-things-snapshots"
        );
        assertThat(underTest.getAutoStartPluginIds("things"))
                .as("things auto-start set must match things.conf lines 609-614 exactly (WoT excluded)")
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void thingsAutoStartSetDoesNotContainWot() {
        assertThat(underTest.getAutoStartPluginIds("things"))
                .as("WoT validation-config plugin must NOT be in the things auto-start set")
                .doesNotContain("pekko-contrib-mongodb-persistence-wot-validation-config-journal",
                        "pekko-contrib-mongodb-persistence-wot-validation-config-snapshots");
    }

    @Test
    public void policiesAutoStartSetMatchesShippedHocon() {
        // policies.conf:291-296
        final List<String> expected = List.of(
                "pekko-contrib-mongodb-persistence-policies-journal",
                "pekko-contrib-mongodb-persistence-policies-snapshots"
        );
        assertThat(underTest.getAutoStartPluginIds("policies"))
                .as("policies auto-start set must match policies.conf lines 291-296 exactly")
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void connectivityAutoStartSetMatchesShippedHocon() {
        // connectivity.conf:1227-1232 — main connection journal + snapshots only (not remember-store)
        final List<String> expected = List.of(
                "pekko-contrib-mongodb-persistence-connection-journal",
                "pekko-contrib-mongodb-persistence-connection-snapshots"
        );
        assertThat(underTest.getAutoStartPluginIds("connectivity"))
                .as("connectivity auto-start set must match connectivity.conf lines 1227-1232 exactly")
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void autoStartForUnknownServiceReturnsEmpty() {
        assertThat(underTest.getAutoStartPluginIds("unknown-service"))
                .as("unknown service must return an empty auto-start set (not throw)")
                .isEmpty();
    }

    // ── remember-store plugin IDs (regression guard) ─────────────────────────

    @Test
    public void connectionRememberStoreJournalMatchesShippedHocon() {
        // connectivity.conf:1175
        final PersistencePluginConfig.RememberStorePluginIds ids = underTest.getRememberStorePluginIds("connection");
        assertThat(ids.journalPluginId())
                .as("connection remember-store journal must match connectivity.conf line 1175")
                .isEqualTo("pekko-contrib-mongodb-persistence-connection-remember-journal");
    }

    @Test
    public void connectionRememberStoreSnapshotMatchesShippedHocon() {
        // connectivity.conf:1176
        final PersistencePluginConfig.RememberStorePluginIds ids = underTest.getRememberStorePluginIds("connection");
        assertThat(ids.snapshotPluginId())
                .as("connection remember-store snapshot must match connectivity.conf line 1176")
                .isEqualTo("pekko-contrib-mongodb-persistence-connection-remember-snapshots");
    }

    @Test
    public void rememberStoreForUnknownEntityThrows() {
        assertThatThrownBy(() -> underTest.getRememberStorePluginIds("unknown-entity"))
                .as("getRememberStorePluginIds for an unknown entity must throw IllegalArgumentException")
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── pluginConfig() wiring on the provider ────────────────────────────────

    @Test
    public void providerPluginConfigReturnsMongoPersistencePluginConfig() {
        final var provider = new MongoPersistenceBackendProvider(system, referenceExtensionConfig);
        assertThat(provider.pluginConfig())
                .as("pluginConfig() must not throw and must return a non-null MongoPersistencePluginConfig")
                .isNotNull()
                .isInstanceOf(MongoPersistencePluginConfig.class);
    }
}
