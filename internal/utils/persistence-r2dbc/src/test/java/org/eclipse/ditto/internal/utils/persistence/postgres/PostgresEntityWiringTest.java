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
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Arrays;
import java.util.Collection;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresTableNames;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Verifies the C-3 / M-6 entity-actor wiring contract for the Postgres profile.
 *
 * <h2>C-3 — Policy / Connection / WoT entity-actor plugin-ID resolution</h2>
 * {@code PolicyPersistenceActor}, {@code ConnectionPersistenceActor} and {@code WotValidationConfigPersistenceActor}
 * must resolve their Pekko journal/snapshot plugin IDs through the {@link PostgresPersistenceBackendProvider} (mirroring
 * {@code ThingPersistenceActor}) so that a service running the Postgres profile actually writes to Postgres instead of
 * silently writing to Mongo via hardcoded {@code pekko-contrib-mongodb-persistence-*} IDs (the split-brain failure).
 * This test pins the provider mapping each of those actors now depends on — keyed by the actor's <em>entity type</em>
 * ({@code policy}, {@code connection}, {@code wot-validation-config}) exactly as the actors pass it — against the real
 * shipped {@code ditto-postgres-persistence.conf} profile. Each row asserts both the journal and the snapshot plugin ID.
 *
 * <h2>M-6 — WoT entity-type vs table-prefix spelling</h2>
 * The provider keys WoT under the entity type {@code wot-validation-config}, but the Postgres tables use the prefix
 * {@code wot}. {@link PostgresTableNames#of(String)} must therefore accept {@code wot-validation-config} as an alias and
 * resolve it to the {@code wot_*} tables rather than throwing {@code IllegalArgumentException} (which it did before the
 * fix). Without the alias, wiring WoT through the provider — as the profile documents — would throw the moment the
 * read-journal or any table resolution received the entity-type spelling.
 *
 * <p>All assertions run offline: plugin-ID resolution is a pure HOCON read on the provider (no pool is built), and the
 * table-name resolution is pure string logic.</p>
 */
@RunWith(Parameterized.class)
public final class PostgresEntityWiringTest {

    /**
     * The real shipped Postgres profile — the same {@code ditto-postgres-persistence.conf} the production services
     * include — narrowed to the provider's {@code extension-config}. Asserting against it proves the contract holds for
     * the actual profile the entity actors run under, not a hand-built fixture.
     */
    private static final Config PROFILE_EXTENSION_CONFIG =
            ConfigFactory.parseResources("ditto-postgres-persistence.conf")
                    .getConfig("ditto.extensions.persistence-backend-provider.extension-config");

    private static ActorSystem system;
    private static PostgresPersistenceBackendProvider provider;

    @BeforeClass
    public static void setUp() {
        // The provider's plugin-ID methods are pure config reads and never touch the (lazily-built) pool, but the
        // constructor requires a non-null ActorSystem. ssl.mode=disable keeps the client boot guard quiet should
        // anything ever read the postgres config.
        system = ActorSystem.create("PostgresEntityWiringTest",
                ConfigFactory.parseString("ditto.postgresql.ssl.mode = \"disable\"")
                        .withFallback(ConfigFactory.load()));
        provider = new PostgresPersistenceBackendProvider(system, PROFILE_EXTENSION_CONFIG);
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
        return Arrays.asList(new Object[][]{
                // entityType (exactly as the real actor passes it) -> expected journal id, expected snapshot id
                {"policy", "ditto-postgres-policies-journal", "ditto-postgres-policies-snapshots"},
                {"connection", "ditto-postgres-connections-journal", "ditto-postgres-connections-snapshots"},
                {"wot-validation-config", "ditto-postgres-wot-journal", "ditto-postgres-wot-snapshots"},
        });
    }

    @Parameterized.Parameter
    public String entityType;

    @Parameterized.Parameter(1)
    public String expectedJournalPluginId;

    @Parameterized.Parameter(2)
    public String expectedSnapshotPluginId;

    @Test
    public void providerResolvesPostgresPluginIdsForEntityActor() {
        assertThat(provider.getJournalPluginId(entityType))
                .as("journal plugin id for entity <%s> (C-3: actor must route through the provider)", entityType)
                .isEqualTo(expectedJournalPluginId);
        assertThat(provider.getSnapshotPluginId(entityType))
                .as("snapshot plugin id for entity <%s> (C-3: actor must route through the provider)", entityType)
                .isEqualTo(expectedSnapshotPluginId);
    }

    @Test
    public void wotEntityTypeSpellingResolvesTableNamesInsteadOfThrowing() {
        // M-6: PostgresTableNames.of("wot-validation-config") MUST resolve to the wot_* tables (it threw before the fix).
        assertThatCode(() -> PostgresTableNames.of("wot-validation-config"))
                .as("M-6: the WoT entity-type spelling <wot-validation-config> must alias the <wot> tables")
                .doesNotThrowAnyException();

        final PostgresTableNames byEntityType = PostgresTableNames.of("wot-validation-config");
        final PostgresTableNames byPrefix = PostgresTableNames.of("wot");

        assertThat(byEntityType.entityPrefix()).isEqualTo("wot");
        assertThat(byEntityType.journalTable()).isEqualTo(byPrefix.journalTable());
        assertThat(byEntityType.journalSeqTable()).isEqualTo(byPrefix.journalSeqTable());
        assertThat(byEntityType.snapsTable()).isEqualTo(byPrefix.snapsTable());
    }
}
