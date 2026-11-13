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

import java.util.Arrays;
import java.util.Collection;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.api.SnapshotCodec;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Pins {@link MongoPersistenceBackendProvider} plugin-ID resolution against the REAL shipped
 * {@code reference.conf} of this module (the default backend every Ditto service runs unless it opts into another
 * profile).
 *
 * <h2>Why this test exists — the C-3 default-Mongo regression</h2>
 * Once {@code PolicyPersistenceActor}, {@code ConnectionPersistenceActor} and {@code WotValidationConfigPersistenceActor}
 * resolve their Pekko journal/snapshot plugin IDs through this provider (instead of hardcoding the constants), the
 * provider's {@code pluralize()} fallback ({@code entityType + "s"}) becomes load-bearing — and it is WRONG for every
 * Ditto entity whose shipped Mongo plugin block does not follow the naive "+s" rule:
 * <ul>
 *     <li>{@code policy} &rarr; pluralize {@code "policys"} (real block {@code policies}),</li>
 *     <li>{@code connection} &rarr; pluralize {@code "connections"} (real block {@code connection}),</li>
 *     <li>{@code wot-validation-config} &rarr; pluralize {@code "wot-validation-configs"} (real block
 *     {@code wot-validation-config}).</li>
 * </ul>
 * Without the explicit {@code plugin-ids} overrides in {@code reference.conf}, the three actors fail to initialize on
 * the default backend with {@code "Journal plugin [...] configuration doesn't exist."}. This test reads the provider
 * exactly as the actors do — through the real {@code reference.conf} extension-config — and asserts each resolved ID
 * equals the {@code pekko-contrib-mongodb-persistence-*} block name actually shipped in the service configs
 * (policies.conf / connectivity.conf / things.conf). A revert of the overrides, or a wrong block name, turns this test
 * red.
 */
@RunWith(Parameterized.class)
public final class MongoPersistenceBackendProviderTest {

    /**
     * The provider's {@code extension-config} as loaded from the REAL {@code reference.conf} on the classpath — the
     * same subtree {@code DittoExtensionPoint} hands the provider constructor at runtime. Asserting against this proves
     * the contract holds for the shipped default config, not a hand-built fixture.
     */
    private static final Config REFERENCE_EXTENSION_CONFIG =
            ScopedConfig.dittoExtension(ConfigFactory.load("reference"))
                    .getConfig("persistence-backend-provider.extension-config");

    private static ActorSystem system;
    private static MongoPersistenceBackendProvider provider;

    @BeforeClass
    public static void setUp() {
        // Plugin-ID resolution is a pure config read; the (lazy) MongoReadJournal is never built here. The constructor
        // only needs a non-null ActorSystem.
        system = ActorSystem.create("MongoPersistenceBackendProviderTest", ConfigFactory.load("reference"));
        provider = new MongoPersistenceBackendProvider(system, REFERENCE_EXTENSION_CONFIG);
    }

    @AfterClass
    public static void tearDown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    @Parameterized.Parameters(name = "entityType={0}")
    public static Collection<Object[]> parameters() {
        // entityType (exactly as the actor passes it) -> expected REAL Mongo journal id, expected REAL snapshot id.
        // These names are verified against the shipped service configs:
        //   thing                 -> things.conf:614 / :655 (pluralize happens to be correct here)
        //   policy                -> policies.conf:297 / :342
        //   connection            -> connectivity.conf:1235 / :1280 (NO trailing s)
        //   wot-validation-config -> things.conf:679 / :725        (NO trailing s)
        return Arrays.asList(new Object[][]{
                {"thing", "pekko-contrib-mongodb-persistence-things-journal",
                        "pekko-contrib-mongodb-persistence-things-snapshots"},
                {"policy", "pekko-contrib-mongodb-persistence-policies-journal",
                        "pekko-contrib-mongodb-persistence-policies-snapshots"},
                {"connection", "pekko-contrib-mongodb-persistence-connection-journal",
                        "pekko-contrib-mongodb-persistence-connection-snapshots"},
                {"wot-validation-config", "pekko-contrib-mongodb-persistence-wot-validation-config-journal",
                        "pekko-contrib-mongodb-persistence-wot-validation-config-snapshots"},
        });
    }

    @Parameterized.Parameter
    public String entityType;

    @Parameterized.Parameter(1)
    public String expectedJournalPluginId;

    @Parameterized.Parameter(2)
    public String expectedSnapshotPluginId;

    @Test
    public void resolvesRealMongoBlockNamesForEntityActor() {
        assertThat(provider.getJournalPluginId(entityType))
                .as("Mongo journal plugin id for entity <%s> must match the shipped block "
                        + "(C-3 default-backend regression guard)", entityType)
                .isEqualTo(expectedJournalPluginId);
        assertThat(provider.getSnapshotPluginId(entityType))
                .as("Mongo snapshot plugin id for entity <%s> must match the shipped block "
                        + "(C-3 default-backend regression guard)", entityType)
                .isEqualTo(expectedSnapshotPluginId);
    }

    /**
     * Regression guard: {@link MongoPersistenceBackendProvider#snapshotCodec()} must return a non-null
     * {@link MongoSnapshotCodec} instead of throwing {@link UnsupportedOperationException} (the interface default).
     */
    @Test
    public void snapshotCodecReturnsMongoBsonCodec() {
        final SnapshotCodec codec = provider.snapshotCodec();
        assertThat(codec)
                .as("MongoPersistenceBackendProvider.snapshotCodec() must not throw UOE and must return "
                        + "a MongoSnapshotCodec instance")
                .isNotNull()
                .isInstanceOf(MongoSnapshotCodec.class);
    }

    /**
     * Task 9 regression guard: the string-shorthand extension declaration
     * ({@code ditto.extensions.persistence-backend-provider = "org.eclipse...MongoPersistenceBackendProvider"})
     * wipes the reference.conf {@code extension-config} object, so {@code DittoExtensionPoint.ExtensionIdConfig.of}
     * hands the provider {@link ConfigFactory#empty()}. Without a well-known block-name map, plugin-ID resolution
     * used to fall back to the {@code pluralize()} heuristic and produce non-existent blocks (e.g. {@code "policys"}).
     * This test asserts the REAL shipped block names are resolved even with a completely empty extension config.
     */
    @Test
    public void emptyExtensionConfigResolvesCorrectPluginIdsForAllEntities() {
        final MongoPersistenceBackendProvider emptyOverrideProvider =
                new MongoPersistenceBackendProvider(system, ConfigFactory.empty());
        assertThat(emptyOverrideProvider.getJournalPluginId("thing"))
                .isEqualTo("pekko-contrib-mongodb-persistence-things-journal");
        assertThat(emptyOverrideProvider.getJournalPluginId("policy"))
                .isEqualTo("pekko-contrib-mongodb-persistence-policies-journal");
        assertThat(emptyOverrideProvider.getJournalPluginId("connection"))
                .isEqualTo("pekko-contrib-mongodb-persistence-connection-journal");
        assertThat(emptyOverrideProvider.getJournalPluginId("wot-validation-config"))
                .isEqualTo("pekko-contrib-mongodb-persistence-wot-validation-config-journal");
        assertThat(emptyOverrideProvider.getSnapshotPluginId("policy"))
                .isEqualTo("pekko-contrib-mongodb-persistence-policies-snapshots");
    }

    /**
     * Task 9 regression guard: an entity type with neither a well-known block name nor an explicit
     * {@code extension-config.plugin-ids} override must fail fast with a {@link DittoConfigError} naming the
     * offending entity type, instead of silently guessing a (possibly non-existent) plugin block via
     * {@code pluralize()}.
     */
    @Test
    public void unknownEntityTypeWithoutOverrideFailsFast() {
        final MongoPersistenceBackendProvider emptyOverrideProvider =
                new MongoPersistenceBackendProvider(system, ConfigFactory.empty());
        assertThatThrownBy(() -> emptyOverrideProvider.getJournalPluginId("gadget"))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("gadget");
    }
}
