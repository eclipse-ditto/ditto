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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresSchemaHealer;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresSqlStates;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.journal.PostgresJournalOps;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.persistence.AtomicWrite;
import org.apache.pekko.persistence.PersistentRepr;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Integration test for the runtime schema self-heal against a real PostgreSQL (PG 16) via Testcontainers. Runs under
 * failsafe ({@code *IT}); skipped offline / when no Docker daemon is reachable.
 * <p>
 * Proves that after tables are dropped out from under a running client:
 * </p>
 * <ol>
 *     <li>a read heals + retries and returns empty (Mongo's "read of a missing collection is empty"), recreating the
 *     tables;</li>
 *     <li>a journal write (through {@code inTransaction}) heals + retries and succeeds;</li>
 *     <li>with self-heal disabled, the read still fails with {@code 42P01} (no worse than today).</li>
 * </ol>
 */
public final class PostgresSelfHealIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static final long TIMEOUT_SECONDS = 20L;

    private static ActorSystem system;
    private static Materializer mat;
    private static ConnectionFactory ddlFactory;

    private DittoPostgresClient client;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresSelfHealIT", t);
        }
        ddlFactory = POSTGRES.newConnectionFactory();
        system = ActorSystem.create("PostgresSelfHealIT");
        mat = SystemMaterializer.get(system).materializer();
    }

    @AfterClass
    public static void stop() {
        if (system != null) {
            system.terminate();
        }
        POSTGRES.stop();
    }

    @Before
    public void ensureSchema() {
        // Restore a fully-created schema before each test (idempotent), so a prior test's DROP does not leak.
        PostgresSchemaManager.of(ddlFactory, PostgresSchema.descriptor()).bootstrap();
    }

    @After
    public void closeClient() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    @Test
    public void readOnAMissingTableHealsAndReturnsEmpty() throws Exception {
        client = newClient(true);
        final PostgresPersistenceOperations operations = PostgresPersistenceOperations.of(client, "things");

        dropThingsTables();
        assertThat(tableExists("things_journal")).as("precondition: tables dropped").isFalse();

        final List<String> pids = await(operations.currentPersistenceIds().runWith(Sink.seq(), mat));

        assertThat(pids).as("read of a missing table self-heals and returns empty").isEmpty();
        assertThat(tableExists("things_journal")).as("the self-heal recreated the tables").isTrue();
        assertThat(tableExists("things_snaps")).isTrue();
    }

    @Test
    public void writeOnAMissingTableHealsAndSucceeds() throws Exception {
        client = newClient(true);
        final PostgresPersistenceOperations operations = PostgresPersistenceOperations.of(client, "things");
        final PostgresJournalOps journal = PostgresJournalOps.of(operations);
        final String pid = "thing:heal:write";

        dropThingsTables();
        assertThat(tableExists("things_journal")).as("precondition: tables dropped").isFalse();

        final List<java.util.Optional<Exception>> results =
                toList(journal.writeMessages(List.of(write(pid, 1L, "{\"v\":1}"))));

        assertThat(results).as("the write reports one slot").hasSize(1);
        assertThat(results.get(0)).as("the healed write succeeded (no rejection)").isEmpty();
        assertThat(countJournal(pid)).as("the row is persisted in the freshly-created table").isEqualTo(1L);
    }

    @Test
    public void readOnAMissingTableFailsWhenSelfHealDisabled() throws Exception {
        client = newClient(false);
        final PostgresPersistenceOperations operations = PostgresPersistenceOperations.of(client, "things");

        dropThingsTables();

        assertThatThrownBy(() -> await(operations.currentPersistenceIds().runWith(Sink.seq(), mat)))
                .as("with self-heal disabled the original 42P01 surfaces")
                .satisfies(error -> assertThat(PostgresSqlStates.isUndefinedTable(error)).isTrue());
        assertThat(tableExists("things_journal")).as("a disabled heal does not recreate the table").isFalse();
    }

    private DittoPostgresClient newClient(final boolean selfHeal) {
        // A client + healer both derived from a config pointing at the container (DDL creds embedded in the URI so the
        // runtime role can CREATE; ssl disabled for the plaintext test container).
        final String hocon = "ditto.postgresql {\n"
                + "  uri = \"" + POSTGRES.getR2dbcUrl() + "\"\n"
                + "  ssl.mode = \"disable\"\n"
                + "  schema.self-heal = " + selfHeal + "\n"
                + "}";
        final PostgresConfig config = DefaultPostgresConfig.of(ConfigFactory.parseString(hocon).getConfig("ditto"));
        assertThat(config.isSchemaSelfHealEnabled()).isEqualTo(selfHeal);
        final PostgresSchemaHealer healer = PostgresSchemaHealer.of(config);
        // In production the persistence bootstrap registers the descriptor (PostgresPersistenceBackendProvider);
        // this test builds the healer directly, so register it here.
        healer.registerDescriptor(PostgresSchema.descriptor());
        return DittoPostgresClient.newInstance(config, healer);
    }

    private static AtomicWrite write(final String pid, final long sn, final String json) {
        return AtomicWrite.apply(PersistentRepr.apply(org.eclipse.ditto.json.JsonFactory.newObject(json), sn, pid,
                "manifest", false, ActorRef.noSender(), "w"));
    }

    private long countJournal(final String pid) throws Exception {
        return await(client.executeSql("SELECT COUNT(*) AS c FROM things_journal WHERE pid = $1",
                        stmt -> stmt.bind(0, pid), (row, meta) -> row.get("c", Long.class))
                .runWith(Sink.head(), mat));
    }

    private static void dropThingsTables() {
        runDdl("DROP TABLE IF EXISTS things_journal, things_journal_seq, things_snaps CASCADE");
    }

    private static boolean tableExists(final String table) {
        return Boolean.parseBoolean(Mono.usingWhen(Mono.from(ddlFactory.create()),
                        conn -> Flux.from(conn.createStatement(
                                        "SELECT EXISTS (SELECT 1 FROM information_schema.tables "
                                                + "WHERE table_name = '" + table + "' AND table_schema = current_schema())")
                                        .execute())
                                .flatMap(result -> result.map((row, meta) -> String.valueOf(row.get(0))))
                                .next(),
                        Connection::close)
                .block());
    }

    private static void runDdl(final String sql) {
        Mono.usingWhen(Mono.from(ddlFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(Result::getRowsUpdated)
                                .then(),
                        Connection::close)
                .block();
    }

    private static <T> T await(final CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static <T> List<T> toList(final CompletionStage<? extends Iterable<T>> stage) throws Exception {
        final Iterable<T> iterable = stage.toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        final List<T> list = new java.util.ArrayList<>();
        iterable.forEach(list::add);
        return list;
    }

}
