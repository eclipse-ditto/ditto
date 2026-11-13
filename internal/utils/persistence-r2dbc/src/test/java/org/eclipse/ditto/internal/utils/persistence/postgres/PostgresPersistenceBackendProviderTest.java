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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.persistence.api.DittoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedPayloadStore;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresClientExtension;
import org.eclipse.ditto.internal.utils.persistence.postgres.payload.PostgresPersistedPayloadStore;
import org.eclipse.ditto.internal.utils.persistence.postgres.readjournal.PostgresReadJournal;
import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;
import org.junit.After;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.r2dbc.pool.ConnectionPool;

/**
 * Unit test for {@link PostgresPersistenceBackendProvider}:
 * <ul>
 *     <li>explicit {@code plugin-ids.<entity>.{journal|snapshot}} resolution (NO pluralize heuristic),</li>
 *     <li>a missing mapping fails clearly with a {@link DittoConfigError},</li>
 *     <li>{@code getReadJournal()} is {@code synchronized}-lazy and returns the same instance on repeated calls,</li>
 *     <li>{@code payloadStore(entityType)} binds to the entity type's table set on the shared pool, which its
 *     {@code close()} leaves open.</li>
 * </ul>
 * All assertions run offline — building the read journal opens an R2DBC pool but never connects (no live PostgreSQL),
 * so {@code ssl.mode=disable} keeps the boot guard quiet.
 */
public final class PostgresPersistenceBackendProviderTest {

    private ActorSystem system;

    @After
    public void tearDown() {
        if (system != null) {
            system.terminate();
        }
    }

    private PostgresPersistenceBackendProvider providerWith(final String extensionHocon) {
        final Config dittoConfig = ConfigFactory.parseString(
                // The provider reads ditto.postgresql for the read-journal pool; disable SSL so no boot guard fires
                // and no connection is ever attempted in these offline tests.
                "ditto.postgresql.uri = \"r2dbc:postgresql://localhost:5432/ditto\"\n"
                        + "ditto.postgresql.username = \"ditto\"\n"
                        + "ditto.postgresql.password = \"secret\"\n"
                        + "ditto.postgresql.ssl.mode = \"disable\"\n");
        system = ActorSystem.create("PostgresPersistenceBackendProviderTest",
                dittoConfig.withFallback(ConfigFactory.load()));
        final Config extensionConfig = ConfigFactory.parseString(extensionHocon);
        return new PostgresPersistenceBackendProvider(system, extensionConfig);
    }

    /** Plugin-ID mappings WITHOUT a {@code read-journal.entity} — used for the missing-key failure test. */
    private static final String PLUGIN_IDS_NO_READ_JOURNAL_ENTITY =
            "plugin-ids {\n"
                    + "  thing.journal       = \"ditto-postgres-things-journal\"\n"
                    + "  thing.snapshot      = \"ditto-postgres-things-snapshots\"\n"
                    + "  connection.journal  = \"ditto-postgres-connections-journal\"\n"
                    + "  connection.snapshot = \"ditto-postgres-connections-snapshots\"\n"
                    + "}\n";

    /**
     * The full mappings PLUS an explicit {@code read-journal.entity}. There is no silent {@code "things"} default
     * anymore (r2mlh-m4), so every fixture that materialises the read journal must declare the entity explicitly,
     * exactly as the per-service configs now do.
     */
    private static final String FULL_PLUGIN_IDS =
            PLUGIN_IDS_NO_READ_JOURNAL_ENTITY + "read-journal.entity = \"things\"\n";

    @Test
    public void resolvesExplicitPluginIds() {
        final PostgresPersistenceBackendProvider provider = providerWith(FULL_PLUGIN_IDS);

        assertThat(provider.getJournalPluginId("thing")).isEqualTo("ditto-postgres-things-journal");
        assertThat(provider.getSnapshotPluginId("thing")).isEqualTo("ditto-postgres-things-snapshots");
    }

    @Test
    public void doesNotPluralize_connectionIsTakenLiterallyFromConfig() {
        // The Mongo provider's pluralize() would turn "connection" -> "connections" and guess a plugin ID.
        // This provider takes the EXACT configured value, so a singular "connection" entity maps to whatever HOCON says.
        final PostgresPersistenceBackendProvider provider = providerWith(FULL_PLUGIN_IDS);

        assertThat(provider.getJournalPluginId("connection")).isEqualTo("ditto-postgres-connections-journal");
        assertThat(provider.getSnapshotPluginId("connection")).isEqualTo("ditto-postgres-connections-snapshots");
    }

    @Test
    public void missingEntityMappingFailsClearly() {
        final PostgresPersistenceBackendProvider provider = providerWith(FULL_PLUGIN_IDS);

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> provider.getJournalPluginId("policy"))
                .withMessageContaining("plugin-ids.policy.journal")
                .withMessageContaining("no pluralize() fallback");
    }

    @Test
    public void missingJournalKeyForKnownEntityFailsClearly() {
        // thing.snapshot present, thing.journal absent.
        final PostgresPersistenceBackendProvider provider = providerWith(
                "plugin-ids.thing.snapshot = \"ditto-postgres-things-snapshots\"\n");

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> provider.getJournalPluginId("thing"))
                .withMessageContaining("plugin-ids.thing.journal");
    }

    @Test
    public void emptyExtensionConfigFailsClearlyForEveryEntity() {
        final PostgresPersistenceBackendProvider provider = providerWith("");

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> provider.getSnapshotPluginId("thing"))
                .withMessageContaining("plugin-ids.thing.snapshot");
    }

    @Test
    public void getReadJournalIsSynchronizedLazyAndIdentityStable() {
        final PostgresPersistenceBackendProvider provider = providerWith(FULL_PLUGIN_IDS);

        final DittoReadJournal first = provider.getReadJournal();
        final DittoReadJournal second = provider.getReadJournal();

        assertThat(first).isInstanceOf(PostgresReadJournal.class);
        // synchronized-lazy: the heavy instance is built once and cached -> the very same object on repeat calls.
        assertThat(second).isSameAs(first);
    }

    @Test
    public void readJournalEntityRejectsTheRememberStoreTableSet() {
        // connection_remember is a table set (schema v4) but no entity: its rows are Pekko envelopes under /sharding/
        // persistence ids, so the ping, cleanup and streaming read journal must never bind to it.
        final PostgresPersistenceBackendProvider provider = providerWith(
                PLUGIN_IDS_NO_READ_JOURNAL_ENTITY + "read-journal.entity = \"connection_remember\"\n");

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(provider::resolveReadJournalEntity)
                .withMessageContaining("connection_remember")
                .withMessageContaining("things|policies|connections|wot");
    }

    @Test
    public void readJournalBindsToConfiguredEntity() {
        final PostgresPersistenceBackendProvider provider = providerWith(
                PLUGIN_IDS_NO_READ_JOURNAL_ENTITY + "read-journal.entity = \"policies\"\n");

        // No exception for a known entity prefix; the read journal materialises lazily.
        assertThat(provider.getReadJournal()).isInstanceOf(PostgresReadJournal.class);
    }

    @Test
    public void unknownReadJournalEntityFailsClearly() {
        final PostgresPersistenceBackendProvider provider = providerWith(
                PLUGIN_IDS_NO_READ_JOURNAL_ENTITY + "read-journal.entity = \"bogus\"\n");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(provider::getReadJournal)
                .withMessageContaining("bogus");
    }

    @Test
    public void missingReadJournalEntityFailsClearly() {
        // r2mlh-m4: there is NO silent "things" default anymore. A service that activates the Postgres profile but
        // forgets to set read-journal.entity must fail fast with a DittoConfigError, not silently bind to things_*.
        final PostgresPersistenceBackendProvider provider = providerWith(PLUGIN_IDS_NO_READ_JOURNAL_ENTITY);

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(provider::getReadJournal)
                .withMessageContaining("read-journal.entity");
    }

    @Test
    public void thingsServiceResolvesThingsEntity() {
        assertPerServiceEntity("things", "things");
    }

    @Test
    public void policiesServiceResolvesPoliciesEntity() {
        assertPerServiceEntity("policies", "policies");
    }

    @Test
    public void connectivityServiceResolvesConnectionsEntity() {
        assertPerServiceEntity("connectivity", "connections");
    }

    /**
     * Loads the named service's real HOCON from the repo source tree and asserts the provider resolves the expected
     * read-journal entity (and does not throw). This pins the per-service {@code read-journal.entity} override added in
     * r2mlh-m4: it parses the actual {@code <service>.conf}, extracts the
     * {@code ditto.extensions.persistence-backend-provider.extension-config} subtree exactly as {@code PekkoClassLoader}
     * would, and runs it through the production {@code resolveReadJournalEntity()}.
     */
    private void assertPerServiceEntity(final String serviceName, final String expectedEntity) {
        final java.io.File serviceConfFile = locateServiceConf(serviceName);
        // Parse UNRESOLVED, extract the extension-config subtree, then resolve only that subtree. Resolving the whole
        // service conf standalone trips on its own cross-conf substitutions (e.g. connectivity-extension.conf appends
        // to ${ditto.extensions.signal-transformers-provider...}, which only resolves with reference.conf on the
        // classpath). The extension-config subtree references only the optional ${?POSTGRES_READ_JOURNAL_ENTITY}, so it
        // resolves cleanly: env unset -> the substitution collapses and the per-service literal stands.
        final Config serviceConfig = ConfigFactory.parseFile(serviceConfFile,
                com.typesafe.config.ConfigParseOptions.defaults().setAllowMissing(false));
        final Config extensionConfig = serviceConfig.getConfig(
                        "ditto.extensions.persistence-backend-provider.extension-config")
                .resolve(com.typesafe.config.ConfigResolveOptions.defaults().setAllowUnresolved(true));
        final Config dittoConfig = ConfigFactory.parseString(
                "ditto.postgresql.uri = \"r2dbc:postgresql://localhost:5432/ditto\"\n"
                        + "ditto.postgresql.username = \"ditto\"\n"
                        + "ditto.postgresql.password = \"secret\"\n"
                        + "ditto.postgresql.ssl.mode = \"disable\"\n");
        system = ActorSystem.create("PostgresPersistenceBackendProviderTest",
                dittoConfig.withFallback(ConfigFactory.load()));
        final PostgresPersistenceBackendProvider provider =
                new PostgresPersistenceBackendProvider(system, extensionConfig);

        assertThat(provider.resolveReadJournalEntity()).isEqualTo(expectedEntity);
    }

    /**
     * Walks up from the surefire working directory (the module dir) to the repo root and resolves the service's
     * {@code src/main/resources/<service>.conf}. The service modules do not depend on this module, so the conf is not
     * on the test classpath; reading it from the source tree keeps the assertion against the real, shipped config.
     */
    private static java.io.File locateServiceConf(final String serviceName) {
        final String relative = serviceName + "/service/src/main/resources/" + serviceName + ".conf";
        java.io.File dir = new java.io.File(System.getProperty("user.dir")).getAbsoluteFile();
        while (dir != null) {
            final java.io.File candidate = new java.io.File(dir, relative);
            if (candidate.isFile()) {
                return candidate;
            }
            dir = dir.getParentFile();
        }
        throw new IllegalStateException("Could not locate " + relative + " from user.dir="
                + System.getProperty("user.dir"));
    }

    @Test
    public void configuredEntitiesReflectsHocon() {
        final PostgresPersistenceBackendProvider provider = providerWith(FULL_PLUGIN_IDS);

        assertThat(provider.configuredEntities()).containsExactlyInAnyOrder("thing", "connection");
    }

    /**
     * A provider whose read journal is bound to {@code "things"} (entity = things) while its plugin-ids also map a
     * {@code wot-validation-config} entity bound to the {@code "wot"} table set — the fixture for the
     * {@code streaming(entityType)} honesty-guard tests below. The per-plugin {@code .entity} keys
     * ({@code ditto-postgres-things-journal.entity}, {@code ditto-postgres-wot-journal.entity}) are the same real
     * config paths the shipped {@code ditto-postgres-persistence.conf} declares at the ROOT of the actor-system
     * config (not under {@code ditto.*}), so they are layered directly onto the system config here rather than into
     * the extension-config subtree.
     */
    private PostgresPersistenceBackendProvider providerForStreamingGuard() {
        final Config dittoConfig = ConfigFactory.parseString(
                "ditto.postgresql.uri = \"r2dbc:postgresql://localhost:5432/ditto\"\n"
                        + "ditto.postgresql.username = \"ditto\"\n"
                        + "ditto.postgresql.password = \"secret\"\n"
                        + "ditto.postgresql.ssl.mode = \"disable\"\n"
                        + "ditto-postgres-things-journal.entity = \"things\"\n"
                        + "ditto-postgres-wot-journal.entity = \"wot\"\n");
        system = ActorSystem.create("PostgresPersistenceBackendProviderStreamingTest",
                dittoConfig.withFallback(ConfigFactory.load()));
        final Config extensionConfig = ConfigFactory.parseString(
                "plugin-ids {\n"
                        + "  thing.journal                  = \"ditto-postgres-things-journal\"\n"
                        + "  thing.snapshot                 = \"ditto-postgres-things-snapshots\"\n"
                        + "  wot-validation-config.journal  = \"ditto-postgres-wot-journal\"\n"
                        + "  wot-validation-config.snapshot = \"ditto-postgres-wot-snapshots\"\n"
                        + "}\n"
                        + "read-journal.entity = \"things\"\n");
        return new PostgresPersistenceBackendProvider(system, extensionConfig);
    }

    @Test
    public void streamingSameBoundEntitySucceeds() {
        final PostgresPersistenceBackendProvider provider = providerForStreamingGuard();

        assertThatCode(() -> provider.streaming("thing", id -> null, entityId -> null))
                .doesNotThrowAnyException();
    }

    @Test
    public void streamingDifferentEntityFailsClearlyNamingBothEntities() {
        final PostgresPersistenceBackendProvider provider = providerForStreamingGuard();

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> provider.streaming("wot-validation-config", id -> null, entityId -> null))
                .withMessageContaining("wot")
                .withMessageContaining("things");
    }

    @Test
    public void payloadStoreForConnectionBindsToTheConnectionsTables() {
        final PostgresPersistenceBackendProvider provider = providerWith(FULL_PLUGIN_IDS);

        final PostgresPersistedPayloadStore store =
                (PostgresPersistedPayloadStore) provider.payloadStore("connection");

        assertThat(store.tables().snapsTable()).isEqualTo("connections_snaps");
        assertThat(store.tables().journalTable()).isEqualTo("connections_journal");
        assertThat(store.progressTable()).isEqualTo(PostgresSchema.ENCRYPTION_MIGRATION_TABLE);
    }

    @Test
    public void payloadStoreForAnUnknownEntityTypeFailsClearly() {
        final PostgresPersistenceBackendProvider provider = providerWith(FULL_PLUGIN_IDS);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> provider.payloadStore("gateway"))
                .withMessageContaining("gateway");
    }

    @Test
    public void payloadStoreCloseLeavesTheSharedPoolOpen() {
        final PostgresPersistenceBackendProvider provider = providerWith(FULL_PLUGIN_IDS);
        final PersistedPayloadStore store = provider.payloadStore("connection");
        final ConnectionPool sharedPool = PostgresClientExtension.get(system).getClient().getConnectionPool();

        store.close();

        // the store reads through the single shared pool, which only the extension disposes (coordinated shutdown)
        assertThat(sharedPool.isDisposed()).isFalse();
        assertThatCode(() -> provider.payloadStore("connection")).doesNotThrowAnyException();
    }

    @Test
    public void buildingReadJournalWiresPoolMetricsPollerAndShutdownHook() {
        // Building the read journal creates the pool and (per the fix) starts the pool-metrics poller and registers a
        // coordinated-shutdown task; both must materialise without a live DB and tear down cleanly via system.terminate().
        final PostgresPersistenceBackendProvider provider = providerWith(FULL_PLUGIN_IDS);

        assertThatCode(provider::getReadJournal).doesNotThrowAnyException();
        // Shutting the system down runs the registered coordinated-shutdown task (poller close + pool dispose) cleanly.
        // TestKit.shutdownActorSystem blocks until termination completes, so it surfaces any failure in the task.
        assertThatCode(() -> TestKit.shutdownActorSystem(system)).doesNotThrowAnyException();
        system = null; // already terminated; avoid a redundant tearDown terminate.
    }

}
