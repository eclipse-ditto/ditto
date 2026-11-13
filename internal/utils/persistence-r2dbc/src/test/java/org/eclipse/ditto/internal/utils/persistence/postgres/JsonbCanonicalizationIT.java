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

import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.journal.PostgresJournalOps;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.readjournal.PostgresReadJournal;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.Result;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.persistence.AtomicWrite;
import org.apache.pekko.persistence.PersistentRepr;
import org.apache.pekko.persistence.query.EventEnvelope;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Testcontainers IT (PG 16) pinning the {@code jsonb} number / key canonicalisation contract for the journal
 * {@code event} column. Decision (probe 2026-06-15 vs PG16): option (i) — keep the column as {@code jsonb} and assert
 * the post-canonical form, rather than switching the column to {@code TEXT} (rejected: loses jsonb indexability,
 * increases storage, and blocks query-side jsonb predicates).
 * <p>
 * The four canonicalisation rules PG16 {@code jsonb} applies to a stored value are:
 * </p>
 * <ol>
 *     <li><b>Object keys are not kept in insertion order</b>, at every nesting level: PG16 orders them by length, then
 *     bytewise (an implementation detail, not a contract; only the canonical-form snapshot test pins it).</li>
 *     <li><b>Scientific notation is flattened</b> to plain decimal ({@code 1E10} → {@code 10000000000},
 *     {@code -2.5E-3} → {@code -0.0025}).</li>
 *     <li><b>Arbitrary-precision integers are preserved</b> beyond the IEEE-754 double range (no float-precision
 *     loss) — {@code 9007199254740993} survives intact.</li>
 *     <li><b>Whitespace inside string keys/values is preserved</b>; inter-token whitespace is stripped.</li>
 * </ol>
 * <p>
 * Two tests guard this:
 * </p>
 * <ul>
 *     <li>the <b>semantic-equality</b> test is the production contract — consumers re-parse the recovered payload via
 *     {@link JsonFactory}, so the round-trip must be key/value equal to the input;</li>
 *     <li>the <b>canonical-form snapshot</b> test pins the raw {@code event::text} so a future PG upgrade that changes
 *     the canonicalisation surfaces as a test break rather than a silent divergence.</li>
 * </ul>
 */
public final class JsonbCanonicalizationIT {

    /**
     * Probe payload exercising all four canonicalisation rules: an integer-valued float ({@code 21.0}), positive and
     * negative scientific notation, an arbitrary-precision integer beyond {@code 2^53}, an unsorted nested object, and a
     * string key with significant internal/leading/trailing whitespace.
     */
    private static final String PROBE_INPUT =
            "{\"intish\": 21.0, \"expish\": 1E10, \"bigint\": 9007199254740993, "
                    + "\"negexp\": -2.5E-3, \"keys\":{\"b\":1,\"a\":2}, \"  ws  \":\"x\"}";

    /**
     * The documented canonical {@code event::text} form PG16 emits for {@link #PROBE_INPUT} (probe run 2026-06-15).
     * Note: jsonb reorders keys at every level (PG16: by length, then bytewise, so {@code keys} comes first and the
     * nested object becomes {@code {"a": 2, "b": 1}}). That order is NOT a contract — only the per-value
     * canonicalisation (rules 2-4) is; this snapshot pins PG16's exact output as a regression guard.
     */
    private static final String CANONICAL_FORM =
            "{\"keys\": {\"a\": 2, \"b\": 1}, \"  ws  \": \"x\", \"bigint\": 9007199254740993, "
                    + "\"expish\": 10000000000, \"intish\": 21.0, \"negexp\": -0.0025}";

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ActorSystem system;
    private static Materializer mat;
    private static ConnectionFactory ddlFactory;
    private static DittoPostgresClient client;
    private static PostgresJournalOps journal;
    private static PostgresReadJournal readJournal;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping JsonbCanonicalizationIT", t);
        }
        ddlFactory = POSTGRES.newConnectionFactory();
        PostgresSchemaManager.of(ddlFactory, PostgresSchema.descriptor()).bootstrap();

        system = ActorSystem.create("JsonbCanonicalizationIT");
        mat = SystemMaterializer.get(system).materializer();
        final ConnectionPool pool = new ConnectionPool(ConnectionPoolConfiguration.builder(ddlFactory)
                .name("jsonb-canon-pool").maxSize(4).build());
        client = DittoPostgresClient.forConnectionPool(pool, DefaultPostgresConfig.of(ConfigFactory.empty()));
        final PostgresPersistenceOperations operations = PostgresPersistenceOperations.of(client, "things");
        journal = PostgresJournalOps.of(operations);
        readJournal = PostgresReadJournal.of(operations);
    }

    @AfterClass
    public static void stop() {
        if (client != null) {
            client.close();
        }
        if (system != null) {
            system.terminate();
        }
        POSTGRES.stop();
    }

    // =================================================================================================
    // semantic equality (THE CONTRACT): round-trip the probe payload through journal write -> readPayload ->
    // JsonFactory.newObject; the recovered payload must be key/value equal to the input. This is what production
    // consumers do (they re-parse the recovered event via JsonFactory), so jsonb canonicalisation must be
    // semantics-preserving.
    // =================================================================================================

    @Test
    public void probePayloadRoundTripsSemanticallyEqualViaJsonFactory() throws Exception {
        final String pid = "thing:m10:semantic";
        final JsonObject input = JsonFactory.newObject(PROBE_INPUT);
        await(journal.writeMessages(List.of(write(pid, 1L, input.toString()))));

        final List<EventEnvelope> envelopes = readJournal.currentEventsByPersistenceId(pid, 0L, Long.MAX_VALUE)
                .runWith(Sink.seq(), mat).toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertThat(envelopes).hasSize(1);

        final JsonObject recovered = readJournal.toEventJson(envelopes.get(0));
        // The contract is KEY-VALUE (semantic) equality, NOT field-order equality: jsonb reorders top-level fields
        // (which jsonb does not promise) and sorts nested-object keys, so JsonObject.equals — which is order-sensitive
        // at the top level — is the wrong assertion here. Assert the same key set and per-key value equality instead.
        // Ditto JSON value equality is numerically semantic (1E10 == 10000000000, -2.5E-3 == -0.0025, 21.0 == 21.0,
        // the arbitrary-precision 9007199254740993 is preserved, and the sorted nested {a,b} object is per-key equal),
        // so this round-trip is semantics-preserving — exactly what a production consumer that re-parses via
        // JsonFactory observes.
        assertThat(recovered.getKeys()).containsExactlyInAnyOrderElementsOf(input.getKeys());
        for (final org.eclipse.ditto.json.JsonKey key : input.getKeys()) {
            assertThat(recovered.getValue(key))
                    .as("value for key '%s' round-trips semantically equal", key)
                    .isEqualTo(input.getValue(key));
        }
    }

    // =================================================================================================
    // canonical-form snapshot (REGRESSION GUARD): the raw event::text PG16 emits for the probe must match the
    // documented canonical form. A future PG upgrade that changes jsonb canonicalisation then surfaces as a test
    // break, not a silent divergence.
    // =================================================================================================

    @Test
    public void probePayloadCanonicalTextMatchesDocumentedForm() throws Exception {
        final String pid = "thing:m10:canonical";
        await(journal.writeMessages(List.of(write(pid, 1L, JsonFactory.newObject(PROBE_INPUT).toString()))));

        final String eventText = eventTextOf(pid);
        assertThat(eventText).isEqualTo(CANONICAL_FORM);
    }

    // =================================================================================================
    // SQLSTATE class-22 (data_exception) rejection: a domain event whose string value contains U+0000 serializes,
    // via Ditto's REAL JsonCharEscaper, to JSON text containing the SIX-CHARACTER escape sequence -- never a raw
    // NUL byte -- so the raw-NUL guard in PostgresJournalOps#toJsonText does not fire. PostgreSQL itself then
    // rejects the jsonb insert with a SQLSTATE class-22 data exception. This must surface as a per-write
    // PostgresJournalOps#writeMessages REJECTION (actor continues), not a failed Future (actor stop + crash loop).
    // =================================================================================================

    @Test
    public void insertingEscapedNulJsonFailsWithSqlStateClass22DataException() throws Exception {
        final String escapedNulJson = JsonObject.newBuilder().set("v", "a" + (char) 0 + "b").build().toString();
        // Sanity: the REAL escaper produced the six-character escape sequence, not a raw NUL byte.
        assertThat(escapedNulJson).doesNotContain(String.valueOf((char) 0)).contains("\\u0000");

        final PostgresPersistenceOperations rawOperations = PostgresPersistenceOperations.of(client, "things");
        final Throwable error = catchThrowable(() -> await(rawOperations.insertEvents(List.of(
                new PostgresPersistenceOperations.JournalInsert("thing:m10:nulinsert", 1L, "manifest", List.of(),
                        escapedNulJson))).toFuture()));

        assertThat(error).as("PostgreSQL must reject the escaped-NUL jsonb insert").isNotNull();
        final Throwable cause = error instanceof ExecutionException && error.getCause() != null
                ? error.getCause() : error;
        assertThat(cause).isInstanceOf(R2dbcException.class);
        final String sqlState = ((R2dbcException) cause).getSqlState();
        assertThat(sqlState).as("SQLSTATE must be class 22 (data_exception), e.g. 22P05").isNotNull()
                .startsWith("22");
    }

    @Test
    public void escapedNulWriteThroughJournalOpsYieldsRejectionNotActorStop() throws Exception {
        final String pid = "thing:m10:nulreject";
        final String escapedNulJson = JsonObject.newBuilder().set("v", "a" + (char) 0 + "b").build().toString();

        final Iterable<Optional<Exception>> slots =
                await(journal.writeMessages(List.of(write(pid, 1L, escapedNulJson))));
        final List<Optional<Exception>> slotList = new ArrayList<>();
        slots.forEach(slotList::add);

        assertThat(slotList).hasSize(1);
        assertThat(slotList.get(0))
                .as("class-22 data exceptions (e.g. 22P05 for the escaped U+0000 sequence) must be a REJECTION, "
                        + "not a failed Future that stops the actor")
                .isPresent();
        assertThat(slotList.get(0).get()).isInstanceOf(R2dbcException.class);
        final String sqlState = ((R2dbcException) slotList.get(0).get()).getSqlState();
        assertThat(sqlState).isNotNull().startsWith("22");
    }

    // =================================================================================================
    // helpers
    // =================================================================================================

    private static <T> T await(final CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    private static AtomicWrite write(final String pid, final long sn, final String json) {
        return AtomicWrite.apply(PersistentRepr.apply(JsonFactory.newObject(json), sn, pid, "manifest", false,
                ActorRef.noSender(), "w"));
    }

    /** @return the raw {@code event::text} PG16 emits for the (single) row of {@code pid} — the post-canonical form. */
    private static String eventTextOf(final String pid) {
        return Mono.usingWhen(Mono.from(ddlFactory.create()),
                        conn -> Flux.from(conn.createStatement(
                                        "SELECT event::text AS t FROM things_journal WHERE pid = '" + pid + "'")
                                        .execute())
                                .flatMap(r -> r.map((row, meta) -> row.get("t", String.class)))
                                .next(),
                        Connection::close)
                .block(Duration.ofSeconds(20));
    }

}
