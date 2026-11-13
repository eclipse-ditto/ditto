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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.internal.models.streaming.LowerBound;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchSchema;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.write.PostgresTimestampPersistence;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.javadsl.Sink;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Integration test for the background-sync plumbing of the Postgres things-search backend against a real PostgreSQL
 * (PG 16) via Testcontainers: the {@link PostgresTimestampPersistence} bookmark (upsert/read round-trips) and
 * {@link PostgresThingsSearchPersistence#sudoStreamMetadata} (projection completeness, delete_at filtering, keyset
 * resumption and paging across the fetch size). Metadata rows are seeded via direct SQL so the projected columns are
 * fixed precisely (the C2 write engine's storage of these columns is proven by {@code PostgresSearchWritePathIT}).
 * <p>
 * Skipped offline / when no Docker daemon is reachable (the {@link #startContainer() Assume} guard turns an unreachable
 * Docker into a skip, not a failure).
 * </p>
 */
public final class PostgresSearchSyncIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ConnectionFactory connectionFactory;
    private static ActorSystem system;

    private static final int SMALL_FETCH_SIZE = 5;

    private PostgresTimestampPersistence bookmark;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresSearchSyncIT", t);
        }
        connectionFactory = POSTGRES.newConnectionFactory();
        system = ActorSystem.create("PostgresSearchSyncIT");
    }

    @AfterClass
    public static void stopContainer() {
        if (system != null) {
            system.terminate();
        }
        POSTGRES.stop();
    }

    @Before
    public void bootstrapSchema() {
        runDdl("DROP TABLE IF EXISTS search_flat, search_things, search_sync, schema_version CASCADE");
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap();
        bookmark = PostgresTimestampPersistence.forConnectionFactory(connectionFactory);
    }

    @After
    public void clear() {
        runDdl("TRUNCATE search_flat, search_things, search_sync");
    }

    // ============================================================================================================
    // search_sync bookmark (TimestampPersistence)
    // ============================================================================================================

    @Test
    public void unwrittenBookmarkReadsEmpty() {
        assertThat(getTaggedTimestamp()).isEmpty();
        assertThat(getTimestamp()).isEmpty();
    }

    @Test
    public void taggedTimestampRoundTrips() {
        final Instant ts = Instant.parse("2026-07-04T12:00:00Z");
        setTaggedTimestamp(ts, "my-tag");

        final Optional<Pair<Instant, String>> tagged = getTaggedTimestamp();
        assertThat(tagged).isPresent();
        assertThat(tagged.get().first()).isEqualTo(ts);
        assertThat(tagged.get().second()).isEqualTo("my-tag");
        assertThat(getTimestamp()).contains(ts);
    }

    @Test
    public void untaggedTimestampRoundTripsWithNullTag() {
        final Instant ts = Instant.parse("2026-07-04T13:30:00Z");
        setTimestamp(ts);

        final Optional<Pair<Instant, String>> tagged = getTaggedTimestamp();
        assertThat(tagged).isPresent();
        assertThat(tagged.get().first()).isEqualTo(ts);
        assertThat(tagged.get().second()).as("an untagged timestamp reads back a null tag").isNull();
    }

    @Test
    public void lastWriteWins() {
        setTaggedTimestamp(Instant.parse("2026-07-04T10:00:00Z"), "first");
        final Instant latest = Instant.parse("2026-07-04T11:00:00Z");
        setTaggedTimestamp(latest, "second");

        final Optional<Pair<Instant, String>> tagged = getTaggedTimestamp();
        assertThat(tagged).isPresent();
        assertThat(tagged.get().first()).isEqualTo(latest);
        assertThat(tagged.get().second()).isEqualTo("second");
        // single-row invariant: exactly one bookmark row is kept.
        assertThat(scalar("SELECT count(*) FROM search_sync")).isEqualTo("1");
    }

    // ============================================================================================================
    // sudoStreamMetadata — projection completeness
    // ============================================================================================================

    @Test
    public void metadataProjectionCarriesRevisionModifiedAndPolicyTagsWithRevisions() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto", "thing-meta");
        final PolicyId ownPolicy = PolicyId.of("org.eclipse.ditto", "policy-own");
        final PolicyId importedPolicy = PolicyId.of("org.eclipse.ditto", "policy-imported");
        final Instant modified = Instant.parse("2026-07-04T10:15:30Z");
        insertRow(thingId, 7L, ownPolicy, 4L,
                referencedPoliciesJson(PolicyTag.of(ownPolicy, 4L), PolicyTag.of(importedPolicy, 2L)),
                modified, null);

        final List<Metadata> metadataList = streamMetadata(fromStart());
        assertThat(metadataList).hasSize(1);
        final Metadata metadata = metadataList.get(0);

        assertThat(metadata.getThingId().toString()).isEqualTo(thingId.toString());
        assertThat(metadata.getThingRevision()).isEqualTo(7L);
        assertThat(metadata.getModified()).contains(modified);
        // thing policy tag carries BOTH id and revision.
        assertThat(metadata.getThingPolicyTag()).contains(PolicyTag.of(ownPolicy, 4L));
        // all referenced policy tags carry id + revision (the C2 review adjudication — needed by isPolicyTagUpToDate).
        assertThat(metadata.getAllReferencedPolicyTags())
                .contains(PolicyTag.of(ownPolicy, 4L), PolicyTag.of(importedPolicy, 2L));
    }

    @Test
    public void deleteAtMarkedRowsAreExcludedFromMetadataStream() {
        final ThingId kept = ThingId.of("ns.keep", "thing-kept");
        final ThingId purged = ThingId.of("ns.purge", "thing-purged");
        final PolicyId policy = PolicyId.of("org.eclipse.ditto", "p");
        insertRow(kept, 1L, policy, 1L, referencedPoliciesJson(PolicyTag.of(policy, 1L)), Instant.now(), null);
        // mark with the epoch-0 delete_at marker exactly as the namespace purge does.
        insertRow(purged, 1L, policy, 1L, referencedPoliciesJson(PolicyTag.of(policy, 1L)), Instant.now(),
                Instant.EPOCH);

        final List<String> streamed = streamMetadata(fromStart()).stream()
                .map(metadata -> metadata.getThingId().toString()).collect(Collectors.toList());
        assertThat(streamed).containsExactly(kept.toString());
    }

    // ============================================================================================================
    // sudoStreamMetadata — keyset resumption + paging across the fetch size
    // ============================================================================================================

    @Test
    public void lowerBoundResumesStrictlyAfterTheGivenThingId() {
        final PolicyId policy = PolicyId.of("org.eclipse.ditto", "p");
        for (int i = 0; i < 6; i++) {
            final ThingId thingId = ThingId.of("org.eclipse.ditto", String.format("thing-%02d", i));
            insertRow(thingId, 1L, policy, 1L, referencedPoliciesJson(PolicyTag.of(policy, 1L)), Instant.now(), null);
        }

        final ThingId lowerBound = ThingId.of("org.eclipse.ditto", "thing-02");
        final List<String> streamed = streamMetadata(lowerBound).stream()
                .map(metadata -> metadata.getThingId().toString()).collect(Collectors.toList());

        assertThat(streamed).containsExactly(
                "org.eclipse.ditto:thing-03",
                "org.eclipse.ditto:thing-04",
                "org.eclipse.ditto:thing-05");
    }

    @Test
    public void keysetStreamsAllRowsAcrossFetchSizeInThingIdOrder() {
        final int total = 12; // > SMALL_FETCH_SIZE (5), so the server-side cursor pages more than once.
        final PolicyId policy = PolicyId.of("org.eclipse.ditto", "p");
        for (int i = 0; i < total; i++) {
            final ThingId thingId = ThingId.of("org.eclipse.ditto", String.format("thing-%03d", i));
            insertRow(thingId, 1L, policy, 1L, referencedPoliciesJson(PolicyTag.of(policy, 1L)), Instant.now(), null);
        }

        final List<String> streamed = streamMetadata(fromStart()).stream()
                .map(metadata -> metadata.getThingId().toString()).collect(Collectors.toList());

        assertThat(streamed).hasSize(total);
        // strictly ascending thing_id order (keyset / ORDER BY thing_id).
        assertThat(streamed).isSorted();
        assertThat(streamed.get(0)).isEqualTo("org.eclipse.ditto:thing-000");
        assertThat(streamed.get(total - 1)).isEqualTo("org.eclipse.ditto:thing-011");
    }

    // ============================================================================================================
    // helpers
    // ============================================================================================================

    private static ThingId fromStart() {
        return ThingId.of(LowerBound.emptyEntityId(ThingConstants.ENTITY_TYPE));
    }

    private List<Metadata> streamMetadata(final ThingId lowerBound) {
        final PostgresThingsSearchPersistence persistence =
                PostgresThingsSearchPersistence.forConnectionFactory(connectionFactory, SMALL_FETCH_SIZE);
        try {
            return persistence.sudoStreamMetadata(lowerBound)
                    .runWith(Sink.seq(), system)
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("sudoStreamMetadata failed", e);
        }
    }

    private void setTimestamp(final Instant ts) {
        try {
            bookmark.setTimestamp(ts).runWith(Sink.ignore(), system).toCompletableFuture().get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("setTimestamp failed", e);
        }
    }

    private void setTaggedTimestamp(final Instant ts, final String tag) {
        try {
            bookmark.setTaggedTimestamp(ts, tag).runWith(Sink.ignore(), system)
                    .toCompletableFuture().get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("setTaggedTimestamp failed", e);
        }
    }

    private Optional<Pair<Instant, String>> getTaggedTimestamp() {
        try {
            return bookmark.getTaggedTimestamp().runWith(Sink.head(), system)
                    .toCompletableFuture().get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("getTaggedTimestamp failed", e);
        }
    }

    private Optional<Instant> getTimestamp() {
        try {
            return bookmark.getTimestampAsync().runWith(Sink.head(), system)
                    .toCompletableFuture().get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("getTimestampAsync failed", e);
        }
    }

    private static String referencedPoliciesJson(final PolicyTag... tags) {
        final org.eclipse.ditto.json.JsonArrayBuilder builder = org.eclipse.ditto.json.JsonArray.newBuilder();
        for (final PolicyTag tag : tags) {
            builder.add(tag.toJson());
        }
        return builder.build().toString();
    }

    /** Seeds one {@code search_things} row with exactly the columns {@code sudoStreamMetadata} projects. */
    private void insertRow(final ThingId thingId, final long revision, final PolicyId policyId,
            final long policyRevision, final String referencedPoliciesJson, final Instant modified,
            final Instant deleteAt) {
        Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> {
                            final Statement statement = conn.createStatement(
                                    "INSERT INTO search_things (thing_id, namespace, revision, policy_id, policy_rev, "
                                            + "referenced_policies, thing, t_modified, delete_at) "
                                            + "VALUES ($1, $2, $3, $4, $5, $6::jsonb, $7::jsonb, $8, $9)");
                            statement.bind(0, thingId.toString());
                            statement.bind(1, thingId.getNamespace());
                            statement.bind(2, revision);
                            statement.bind(3, policyId.toString());
                            statement.bind(4, policyRevision);
                            statement.bind(5, referencedPoliciesJson);
                            statement.bind(6, "{}");
                            statement.bind(7, modified);
                            if (deleteAt != null) {
                                statement.bind(8, deleteAt);
                            } else {
                                statement.bindNull(8, Instant.class);
                            }
                            return Flux.from(statement.execute()).flatMap(Result::getRowsUpdated).then();
                        },
                        Connection::close)
                .block();
    }

    private static String scalar(final String sql) {
        return Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(result -> result.map((row, meta) -> String.valueOf(row.get(0))))
                                .next(),
                        Connection::close)
                .block();
    }

    private static void runDdl(final String sql) {
        Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(Result::getRowsUpdated)
                                .then(),
                        Connection::close)
                .block();
    }

}
