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

import java.util.ArrayList;
import java.util.List;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * HOCON drift test: asserts that {@link MongoPersistencePluginConfig#getAutoStartPluginIds(String)} returns the SAME
 * set as the union of {@code ditto.persistence-backend.auto-start.<entity>.journals} and
 * {@code ditto.persistence-backend.auto-start.<entity>.snapshots} declared by the shipped MongoDB persistence profile
 * {@code ditto-mongo-persistence.conf}.
 *
 * <h2>Purpose</h2>
 * The profile's per-entity auto-start keys are authoritative: each service conf maps its own entity's keys onto
 * {@code pekko.persistence.journal.auto-start-journals} and
 * {@code pekko.persistence.snapshot-store.auto-start-snapshot-stores}, which Pekko reads at boot. The Java constants in
 * {@link MongoPersistencePluginConfig} mirror them. This test is the regression guard that catches DRIFT between
 * the Java constants and the profile: if an entity's auto-start keys are updated in the profile without a
 * corresponding update to the Java constants (or vice versa), this test fails.
 *
 * <h2>How it loads the config</h2>
 * The profile ships in this module's {@code src/main/resources} and is therefore on the test classpath. It is
 * self-contained (its only include is {@code ditto-mongo.conf} of the same module), so it is parsed and resolved on
 * its own.
 *
 * <h2>WoT exclusion</h2>
 * The {@code pekko-contrib-mongodb-persistence-wot-validation-config-*} IDs appear in the profile as Pekko plugin
 * blocks but are intentionally NOT in any auto-start list (WoT is not auto-started). The test explicitly asserts that
 * neither WoT ID appears in the things auto-start set, mirroring the intentional exclusion documented in
 * {@code MongoPersistencePluginConfig}.
 *
 * @since 4.0.0
 */
@RunWith(Parameterized.class)
public final class MongoAutoStartIdsMatchServiceHoconTest {

    private static final String PROFILE_RESOURCE = "ditto-mongo-persistence.conf";
    private static final String AUTO_START_PATH = "ditto.persistence-backend.auto-start";

    private static ActorSystem system;
    private static MongoPersistencePluginConfig pluginConfig;

    @BeforeClass
    public static void setUpClass() {
        final Config extensionConfig =
                ScopedConfig.dittoExtension(ConfigFactory.load("reference"))
                        .getConfig("persistence-backend-provider.extension-config");
        system = ActorSystem.create("MongoAutoStartIdsMatchServiceHoconTest", ConfigFactory.load("reference"));
        final MongoPersistenceBackendProvider provider = new MongoPersistenceBackendProvider(system, extensionConfig);
        pluginConfig = MongoPersistencePluginConfig.of(provider);
    }

    @AfterClass
    public static void tearDownClass() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    /**
     * Each parameter row: [serviceName, confFileName, entityKey]
     * The entity key is the profile's {@code ditto.persistence-backend.auto-start.<entityKey>} key the service conf
     * maps onto its Pekko auto-start lists; it differs for connectivity (entity key = "connections").
     */
    @Parameterized.Parameters(name = "service={0}")
    public static List<Object[]> parameters() {
        return List.of(
                new Object[]{"things",        "things",        "things"},
                new Object[]{"policies",      "policies",      "policies"},
                new Object[]{"connectivity",  "connectivity",  "connections"}
        );
    }

    @Parameterized.Parameter(0)
    public String serviceName;

    @Parameterized.Parameter(1)
    public String confBaseName;

    @Parameterized.Parameter(2)
    public String entityKey;

    // ── main assertion ────────────────────────────────────────────────────────

    /**
     * Reads the profile's two {@code auto-start.<entityKey>.*} lists and asserts they equal
     * {@code pluginConfig.getAutoStartPluginIds(serviceName)}.
     */
    @Test
    public void autoStartPluginIdsMatchShippedServiceHocon() {
        final List<String> hoconAutoStartIds = loadProfileAutoStartIds(entityKey);
        final List<String> javaAutoStartIds = pluginConfig.getAutoStartPluginIds(serviceName);

        assertThat(javaAutoStartIds)
                .as("MongoPersistencePluginConfig.getAutoStartPluginIds(\"%s\") must equal "
                                + "the union of %s.%s.journals + %s.%s.snapshots from %s, which %s.conf maps "
                                + "onto pekko.persistence.* (drift guard)",
                        serviceName, AUTO_START_PATH, entityKey, AUTO_START_PATH, entityKey, PROFILE_RESOURCE,
                        confBaseName)
                .containsExactlyInAnyOrderElementsOf(hoconAutoStartIds);
    }

    // ── WoT exclusion guard (things only) ────────────────────────────────────

    /**
     * Extra guard for the things service: the WoT validation-config Pekko plugin blocks exist in things.conf but
     * are intentionally absent from the auto-start lists. This asserts that {@code getAutoStartPluginIds("things")}
     * does NOT contain either WoT ID, catching any future accidental inclusion.
     */
    @Test
    public void thingsAutoStartSetDoesNotContainWot() {
        if (!"things".equals(serviceName)) {
            return; // WoT guard applies only to things
        }
        assertThat(pluginConfig.getAutoStartPluginIds("things"))
                .as("WoT plugin IDs must NOT be in the things auto-start set "
                        + "(intentionally excluded per things.conf — WoT is not auto-started)")
                .doesNotContain(
                        "pekko-contrib-mongodb-persistence-wot-validation-config-journal",
                        "pekko-contrib-mongodb-persistence-wot-validation-config-snapshots");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Parses and resolves the shipped profile from the classpath and reads the entity's journal and snapshot
     * auto-start lists. Both must be present: a missing key would leave the service with nothing to auto-start.
     */
    private static List<String> loadProfileAutoStartIds(final String entityKey) {
        final Config profile = ConfigFactory.parseResources(PROFILE_RESOURCE).resolve();
        final String entityPath = AUTO_START_PATH + "." + entityKey;

        final List<String> ids = new ArrayList<>(profile.getStringList(entityPath + ".journals"));
        ids.addAll(profile.getStringList(entityPath + ".snapshots"));
        return ids;
    }
}
