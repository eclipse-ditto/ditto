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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.persistence.api.PersistencePluginConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.journal.PostgresJournal;
import org.eclipse.ditto.internal.utils.persistence.postgres.snapshot.PostgresSnapshotStore;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit tests for {@link PostgresPluginWiringCheck}, the boot-time check that the Pekko persistence plugins a deployment
 * wires belong to PostgreSQL when the PostgreSQL provider is selected.
 * <p>
 * Exercised with synthetic configs and a stub {@link PersistencePluginConfig}: no actor system, no database. One test
 * runs the check against the shipped {@code ditto-postgres-persistence.conf} for every service, so a plugin class
 * renamed in code without updating the profile (or vice versa) fails here instead of at the first PostgreSQL boot.
 */
public final class PostgresPluginWiringCheckTest {

    private static final String POSTGRES_JOURNAL_CLASS =
            "org.eclipse.ditto.internal.utils.persistence.postgres.journal.PostgresJournal";
    private static final String POSTGRES_SNAPSHOT_CLASS =
            "org.eclipse.ditto.internal.utils.persistence.postgres.snapshot.PostgresSnapshotStore";
    private static final String MONGO_JOURNAL_CLASS = "pekko.contrib.persistence.mongodb.MongoJournal";

    private static final String THINGS_JOURNAL_ID = "ditto-postgres-things-journal";
    private static final String THINGS_SNAPSHOT_ID = "ditto-postgres-things-snapshots";
    private static final String CONNECTIONS_JOURNAL_ID = "ditto-postgres-connections-journal";
    private static final String CONNECTIONS_SNAPSHOT_ID = "ditto-postgres-connections-snapshots";
    private static final String MONGO_JOURNAL_ID = "pekko-contrib-mongodb-persistence-things-journal";
    private static final String NESTED_JOURNAL_ID = "acme.persistence.pg-journal";

    private static final String JOURNALS_LIST = "pekko.persistence.journal.auto-start-journals";
    private static final String SNAPSHOT_STORES_LIST = "pekko.persistence.snapshot-store.auto-start-snapshot-stores";

    private static final String THINGS = "things";
    private static final String CONNECTIVITY = "connectivity";
    private static final String SERVICE_WITHOUT_DECLARED_IDS = "search";

    private static final PersistencePluginConfig THINGS_PLUGIN_CONFIG =
            new StubPluginConfig(List.of(THINGS_JOURNAL_ID, THINGS_SNAPSHOT_ID));
    private static final PersistencePluginConfig NO_DECLARED_IDS = new StubPluginConfig(List.of());

    @Test
    public void shippedPostgresPluginClassesPass() {
        final Config config = pluginBlocks(
                THINGS_JOURNAL_ID, POSTGRES_JOURNAL_CLASS,
                THINGS_SNAPSHOT_ID, POSTGRES_SNAPSHOT_CLASS);

        assertThatCode(() -> PostgresPluginWiringCheck.verify(config, THINGS_PLUGIN_CONFIG, THINGS))
                .doesNotThrowAnyException();
    }

    @Test
    public void shippedProfileWiresOnlyPostgresClassesForEveryService() {
        // The real profile: its four-entity auto-start lists make leg (b) visit every shipped plugin block.
        final Config profile = ConfigFactory.parseResources("ditto-postgres-persistence.conf").resolve();
        final Map<String, List<String>> declaredIds = Map.of(
                THINGS, List.of(THINGS_JOURNAL_ID, THINGS_SNAPSHOT_ID),
                "policies", List.of("ditto-postgres-policies-journal", "ditto-postgres-policies-snapshots"),
                CONNECTIVITY, List.of(CONNECTIONS_JOURNAL_ID, CONNECTIONS_SNAPSHOT_ID));

        declaredIds.forEach((service, ids) ->
                assertThatCode(() -> PostgresPluginWiringCheck.verify(profile, new StubPluginConfig(ids), service))
                        .as("shipped profile, service <%s>", service)
                        .doesNotThrowAnyException());
    }

    @Test
    public void declaredJournalIdWiredToMongoClassThrowsNamingPluginIdAndActualVsExpected() {
        // Provider = PostgreSQL, but the journal plugin block still carries the MongoDB class (mis-wired HOCON).
        final Config config = pluginBlocks(
                THINGS_JOURNAL_ID, MONGO_JOURNAL_CLASS,
                THINGS_SNAPSHOT_ID, POSTGRES_SNAPSHOT_CLASS);

        assertThatThrownBy(() -> PostgresPluginWiringCheck.verify(config, THINGS_PLUGIN_CONFIG, THINGS))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("PostgreSQL")
                .hasMessageContaining(THINGS_JOURNAL_ID)
                .hasMessageContaining(MONGO_JOURNAL_CLASS)
                .hasMessageContaining(PostgresJournal.class.getName())
                .hasMessageContaining(PostgresSnapshotStore.class.getName());
    }

    @Test
    public void declaredSnapshotIdWiredToMongoClassThrowsNamingPluginId() {
        final Config config = pluginBlocks(
                THINGS_JOURNAL_ID, POSTGRES_JOURNAL_CLASS,
                THINGS_SNAPSHOT_ID, "pekko.contrib.persistence.mongodb.MongoSnapshots");

        assertThatThrownBy(() -> PostgresPluginWiringCheck.verify(config, THINGS_PLUGIN_CONFIG, THINGS))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining(THINGS_SNAPSHOT_ID)
                .hasMessageContaining("pekko.contrib.persistence.mongodb.MongoSnapshots");
    }

    @Test
    public void effectiveAutoStartListWiringMongoPluginThrowsEvenWhenDeclaredIdsAreCorrect() {
        // The half-applied overlay: the PostgreSQL blocks are present and correct, but the effective
        // pekko.persistence auto-start list still starts a MongoDB-classed journal plugin.
        final Config config = ConfigFactory.parseString(
                        JOURNALS_LIST + " = [ \"" + MONGO_JOURNAL_ID + "\" ]\n"
                                + SNAPSHOT_STORES_LIST + " = []\n"
                                + MONGO_JOURNAL_ID + ".class = \"" + MONGO_JOURNAL_CLASS + "\"\n")
                .withFallback(pluginBlocks(
                        THINGS_JOURNAL_ID, POSTGRES_JOURNAL_CLASS,
                        THINGS_SNAPSHOT_ID, POSTGRES_SNAPSHOT_CLASS));

        assertThatThrownBy(() -> PostgresPluginWiringCheck.verify(config, THINGS_PLUGIN_CONFIG, THINGS))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining(JOURNALS_LIST)
                .hasMessageContaining(MONGO_JOURNAL_ID)
                .hasMessageContaining(MONGO_JOURNAL_CLASS);
    }

    @Test
    public void declaredIdWithoutClassThrowsNamingPathAndService() {
        // The snapshot block is fine; the journal block the provider declares is missing entirely. The service name
        // is not a substring of the plugin ID, so the assertion proves the message names the service.
        final Config config = ConfigFactory.parseString(
                CONNECTIONS_SNAPSHOT_ID + ".class = \"" + POSTGRES_SNAPSHOT_CLASS + "\"");
        final PersistencePluginConfig connectivity =
                new StubPluginConfig(List.of(CONNECTIONS_JOURNAL_ID, CONNECTIONS_SNAPSHOT_ID));

        assertThatThrownBy(() -> PostgresPluginWiringCheck.verify(config, connectivity, CONNECTIVITY))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("could not resolve a <class>")
                .hasMessageContaining("<" + CONNECTIONS_JOURNAL_ID + ".class>")
                .hasMessageContaining("<" + CONNECTIVITY + ">");
    }

    @Test
    public void effectiveListEntryWithoutBlockThrowsNamingPluginId() {
        // Leg (c) reached through leg (b): the list names a plugin that has no block at all.
        final Config config = ConfigFactory.parseString(JOURNALS_LIST + " = [ \"acme-missing-journal\" ]")
                .withFallback(pluginBlocks(
                        THINGS_JOURNAL_ID, POSTGRES_JOURNAL_CLASS,
                        THINGS_SNAPSHOT_ID, POSTGRES_SNAPSHOT_CLASS));

        assertThatThrownBy(() -> PostgresPluginWiringCheck.verify(config, THINGS_PLUGIN_CONFIG, THINGS))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("could not resolve a <class>")
                .hasMessageContaining("<acme-missing-journal.class>");
    }

    @Test
    public void dottedPluginIdResolvesAsHoconPathAndPasses() {
        // Pekko resolves a plugin ID as a config path, so a namespaced block is a valid PostgreSQL wiring.
        final Config config = ConfigFactory.parseString(
                "acme { persistence { pg-journal { class = \"" + POSTGRES_JOURNAL_CLASS + "\" } } }\n"
                        + THINGS_SNAPSHOT_ID + ".class = \"" + POSTGRES_SNAPSHOT_CLASS + "\"\n"
                        + JOURNALS_LIST + " = [ \"" + NESTED_JOURNAL_ID + "\" ]");
        final PersistencePluginConfig nested = new StubPluginConfig(List.of(NESTED_JOURNAL_ID, THINGS_SNAPSHOT_ID));

        assertThatCode(() -> PostgresPluginWiringCheck.verify(config, nested, THINGS)).doesNotThrowAnyException();
    }

    @Test
    public void dottedPluginIdWiredToMongoClassThrowsThroughTheEffectiveList() {
        final Config config = ConfigFactory.parseString(
                "acme { persistence { pg-journal { class = \"" + MONGO_JOURNAL_CLASS + "\" } } }\n"
                        + JOURNALS_LIST + " = [ \"" + NESTED_JOURNAL_ID + "\" ]");

        assertThatThrownBy(() -> PostgresPluginWiringCheck.verify(config, NO_DECLARED_IDS, THINGS))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining(NESTED_JOURNAL_ID)
                .hasMessageContaining(MONGO_JOURNAL_CLASS);
    }

    @Test
    public void emptyPluginIdIsRejectedAsTheUnsupportedDefaultPluginShorthand() {
        final Config config = ConfigFactory.parseString(JOURNALS_LIST + " = [ \"\" ]");

        assertThatThrownBy(() -> PostgresPluginWiringCheck.verify(config, NO_DECLARED_IDS, THINGS))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("default plugin");
    }

    @Test
    public void nonStringClassValueIsAConfigError() {
        final Config config = ConfigFactory.parseString(
                THINGS_JOURNAL_ID + " { class { nested = true } }\n"
                        + THINGS_SNAPSHOT_ID + ".class = \"" + POSTGRES_SNAPSHOT_CLASS + "\"");

        assertThatThrownBy(() -> PostgresPluginWiringCheck.verify(config, THINGS_PLUGIN_CONFIG, THINGS))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining(THINGS_JOURNAL_ID);
    }

    @Test
    public void nonListAutoStartValueIsAConfigError() {
        final Config config = ConfigFactory.parseString(JOURNALS_LIST + " = \"" + THINGS_JOURNAL_ID + "\"")
                .withFallback(pluginBlocks(
                        THINGS_JOURNAL_ID, POSTGRES_JOURNAL_CLASS,
                        THINGS_SNAPSHOT_ID, POSTGRES_SNAPSHOT_CLASS));

        assertThatThrownBy(() -> PostgresPluginWiringCheck.verify(config, THINGS_PLUGIN_CONFIG, THINGS))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining(JOURNALS_LIST)
                .hasMessageContaining("not a list");
    }

    @Test
    public void serviceWithoutDeclaredIdsPassesWhenEffectiveListsAreClean() {
        final Config config = ConfigFactory.parseString(JOURNALS_LIST + " = []\n" + SNAPSHOT_STORES_LIST + " = []");

        assertThatCode(() -> PostgresPluginWiringCheck.verify(config, NO_DECLARED_IDS, SERVICE_WITHOUT_DECLARED_IDS))
                .doesNotThrowAnyException();
    }

    @Test
    public void serviceWithoutDeclaredIdsStillFailsOnMongoAutoStartEntry() {
        final Config config = ConfigFactory.parseString(
                SNAPSHOT_STORES_LIST + " = [ \"" + MONGO_JOURNAL_ID + "\" ]\n"
                        + MONGO_JOURNAL_ID + ".class = \"" + MONGO_JOURNAL_CLASS + "\"");

        assertThatThrownBy(() ->
                PostgresPluginWiringCheck.verify(config, NO_DECLARED_IDS, SERVICE_WITHOUT_DECLARED_IDS))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining(SNAPSHOT_STORES_LIST)
                .hasMessageContaining(MONGO_JOURNAL_ID);
    }

    @Test
    public void nullArgumentsThrowNullPointerException() {
        final Config config = ConfigFactory.empty();

        assertThatThrownBy(() -> PostgresPluginWiringCheck.verify(null, NO_DECLARED_IDS, THINGS))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PostgresPluginWiringCheck.verify(config, null, THINGS))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PostgresPluginWiringCheck.verify(config, NO_DECLARED_IDS, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static Config pluginBlocks(final String journalId, final String journalClass,
            final String snapshotId, final String snapshotClass) {
        return ConfigFactory.parseString(
                journalId + ".class = \"" + journalClass + "\"\n" + snapshotId + ".class = \"" + snapshotClass + "\"");
    }

    /** Declares the auto-start plugin IDs for every service name; the per-entity getters are not used by the check. */
    private static final class StubPluginConfig implements PersistencePluginConfig {

        private final List<String> autoStartIds;

        private StubPluginConfig(final List<String> autoStartIds) {
            this.autoStartIds = autoStartIds;
        }

        @Override
        public String getJournalPluginId(final String entityType) {
            throw new UnsupportedOperationException("not used by the wiring check");
        }

        @Override
        public String getSnapshotPluginId(final String entityType) {
            throw new UnsupportedOperationException("not used by the wiring check");
        }

        @Override
        public List<String> getAutoStartPluginIds(final String serviceName) {
            return autoStartIds;
        }

        @Override
        public RememberStorePluginIds getRememberStorePluginIds(final String entityType) {
            throw new UnsupportedOperationException("not used by the wiring check");
        }
    }
}
