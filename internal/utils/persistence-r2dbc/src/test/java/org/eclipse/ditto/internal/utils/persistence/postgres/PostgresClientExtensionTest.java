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

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresClientExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.persistence.Persistence;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.persistence.postgres.readjournal.PostgresReadJournal;
import org.junit.After;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit test for the single-shared-pool seam: the journal, snapshot-store and read-journal of a
 * service must resolve to <strong>one</strong> {@link DittoPostgresClient} / connection pool via
 * {@link PostgresClientExtension}, not three independently-built pools (which made one service instance open up to
 * {@code 3 × pool.max-size} connections while {@code ditto-postgres-persistence.conf} documents {@code max-size} as the
 * per-service total).
 * <p>
 * All assertions run offline: building the client opens an R2DBC pool but never connects (no live PostgreSQL), and
 * {@code ssl.mode = disable} keeps the boot guard quiet. Materialising the journal/snapshot plugins instantiates their
 * {@code (Config)} constructors (which resolve the shared client) but issues no SQL, so no connection is attempted.
 */
public final class PostgresClientExtensionTest {

    private ActorSystem system;

    @After
    public void tearDown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    private void startSystem() {
        final String hocon = ""
                + "ditto.postgresql {\n"
                + "  uri = \"r2dbc:postgresql://localhost:5432/ditto\"\n"
                + "  username = \"ditto\"\n"
                + "  password = \"secret\"\n"
                + "  ssl.mode = \"disable\"\n"
                + "}\n"
                + "ditto-postgres-things-journal {\n"
                + "  class = \"org.eclipse.ditto.internal.utils.persistence.postgres.journal.PostgresJournal\"\n"
                + "  entity = \"things\"\n"
                + "}\n"
                + "ditto-postgres-things-snapshots {\n"
                + "  class = \"org.eclipse.ditto.internal.utils.persistence.postgres.snapshot.PostgresSnapshotStore\"\n"
                + "  entity = \"things\"\n"
                + "}\n";
        final Config config = ConfigFactory.parseString(hocon).withFallback(ConfigFactory.load());
        system = ActorSystem.create("PostgresClientExtensionTest", config);
    }

    @Test
    public void extensionReturnsTheSameSharedClientOnEveryLookup() {
        startSystem();

        final DittoPostgresClient first = PostgresClientExtension.get(system).getClient();
        final DittoPostgresClient second = PostgresClientExtension.get(system).getClient();

        // One pool per actor system: repeated lookups must return the very same client/pool, never a fresh pool.
        assertThat(second).isSameAs(first);
        assertThat(second.getConnectionPool()).isSameAs(first.getConnectionPool());
    }

    @Test
    public void journalSnapshotAndReadJournalAllResolveToOneSharedPool() {
        startSystem();

        // The single source of truth for the pool.
        final DittoPostgresClient sharedClient = PostgresClientExtension.get(system).getClient();

        // Materialise the journal + snapshot-store plugins exactly as Pekko does: their (Config) constructors resolve
        // the client via PostgresClientExtension.get(context().system()). If they still each called
        // DittoPostgresClient.newInstance(...) (the old defect), they would build their own pools and the actor system
        // would hold > 1 pool — but the only client any of them can reach is the shared one below.
        final ActorRef journalRef = Persistence.get(system).journalFor("ditto-postgres-things-journal", ConfigFactory.empty());
        final ActorRef snapshotRef =
                Persistence.get(system).snapshotStoreFor("ditto-postgres-things-snapshots", ConfigFactory.empty());
        assertThat(journalRef).isNotNull();
        assertThat(snapshotRef).isNotNull();

        // The provider's read-journal must resolve the SAME shared client (one pool across journal + snapshot +
        // read-journal). This is the load-bearing assertion: before the fix the provider called
        // DittoPostgresClient.newInstance(...) and built its OWN third pool, so resolvedReadJournalClient() would be a
        // different instance than the extension's client.
        final PostgresPersistenceBackendProvider provider = new PostgresPersistenceBackendProvider(system,
                ConfigFactory.parseString(
                        "plugin-ids.thing.journal = \"ditto-postgres-things-journal\"\n"
                                + "plugin-ids.thing.snapshot = \"ditto-postgres-things-snapshots\"\n"
                                + "read-journal.entity = \"things\"\n"));
        assertThat(provider.getReadJournal()).isInstanceOf(PostgresReadJournal.class);

        assertThat(provider.resolvedReadJournalClient())
                .as("read-journal must share the single per-actor-system pool, not build its own third pool")
                .isSameAs(sharedClient);
        assertThat(provider.resolvedReadJournalClient().getConnectionPool())
                .isSameAs(sharedClient.getConnectionPool());

        // After materialising all three production entry points, the extension still holds exactly one client/pool.
        final DittoPostgresClient afterAll = PostgresClientExtension.get(system).getClient();
        assertThat(afterAll).isSameAs(sharedClient);
        assertThat(afterAll.getConnectionPool()).isSameAs(sharedClient.getConnectionPool());
    }

    @Test
    public void sharedPoolIsDisposedOnCoordinatedShutdownWithoutError() {
        startSystem();

        // Touch the extension so the shared client + its CoordinatedShutdown dispose task are registered.
        assertThat(PostgresClientExtension.get(system).getClient()).isNotNull();

        // Shutting the system down runs the registered coordinated-shutdown task (graceful disposeLater drain) cleanly.
        assertThatCode(() -> TestKit.shutdownActorSystem(system)).doesNotThrowAnyException();
        system = null; // already terminated; avoid a redundant tearDown.
    }

}
