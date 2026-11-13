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

import java.time.Duration;
import java.util.List;

import org.eclipse.ditto.internal.utils.persistence.api.SnapshotEntry;
import org.eclipse.ditto.internal.utils.persistence.api.SnapshotFilter;
import org.eclipse.ditto.internal.utils.persistence.postgres.readjournal.PostgresReadJournal;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;

/**
 * Real-PostgreSQL characterisation of {@link PostgresReadJournal}'s newest-snapshot stream as its callers use it:
 * background cleanup (no fields, deleted snapshots included, the retention as min age), the connectivity service's
 * connection-id retrieval (no fields, deleted ones excluded) and snapshot streaming (fields requested, a pid filter).
 * <p>
 * Nine pids: lifecycles NULL, ACTIVE and DELETED, a DELETED newest snapshot over a live older one (pid 2) and the
 * other way round (pid 3), two DELETED-only pids next to each other (5, 6: a whole page of them at page size 2), a
 * (pid, sn) tie decided by {@code written_at} (7), a fresh newest snapshot over an old one (8) and a fresh-only pid
 * (9). Page sizes 2 and 3 put page boundaries between the pids.
 * </p>
 */
public final class PostgresNewestSnapshotStreamIT {

    private static final String ENTITY = "things";
    private static final String PREFIX = "thing:heads:";
    /** things.conf history-retention-duration default. */
    private static final Duration RETENTION = Duration.ofDays(3);
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(30);

    /* (pid number, sn, lifecycle, fresh, seconds): fresh = an hour ago; otherwise 300 days ago plus the seconds. */
    private static final String SEED_SNAPSHOTS = "INSERT INTO " + ENTITY + "_snaps"
            + " (pid, sn, snapshot, lifecycle, written_at) "
            + "SELECT $1 || lpad(r.i::text, 6, '0'), r.sn, "
            + "jsonb_build_object('thingId', r.i, '_revision', r.sn, '__lifecycle', r.lifecycle), r.lifecycle, "
            + "CASE WHEN r.fresh THEN now() - interval '1 hour' "
            + "ELSE now() - interval '300 days' + r.secs * interval '1 second' END "
            + "FROM (VALUES "
            + "(1, 500, NULL, false, 0), (1, 1000, NULL, false, 1), "
            + "(2, 500, 'ACTIVE', false, 0), (2, 1000, 'DELETED', false, 1), "
            + "(3, 500, 'DELETED', false, 0), (3, 1000, 'ACTIVE', false, 1), "
            + "(4, 500, 'ACTIVE', false, 0), "
            + "(5, 500, 'DELETED', false, 0), "
            + "(6, 500, 'DELETED', false, 0), "
            + "(7, 500, 'ACTIVE', false, 0), (7, 500, 'DELETED', false, 1), "
            + "(8, 500, 'ACTIVE', false, 0), (8, 1000, 'ACTIVE', true, 0), "
            + "(9, 500, 'ACTIVE', true, 0)"
            + ") AS r(i, sn, lifecycle, fresh, secs)";

    /** pid number @ sn @ lifecycle ("-" = none) of every pid's newest snapshot, in pid order. */
    private static final List<String> ALL = List.of("1@1000@-", "2@1000@DELETED", "3@1000@ACTIVE", "4@500@ACTIVE",
            "5@500@DELETED", "6@500@DELETED", "7@500@DELETED", "8@1000@ACTIVE", "9@500@ACTIVE");
    /** Without the pids whose newest snapshot is DELETED: pid 2's older live snapshot is not resurrected. */
    private static final List<String> LIVE =
            List.of("1@1000@-", "3@1000@ACTIVE", "4@500@ACTIVE", "8@1000@ACTIVE", "9@500@ACTIVE");
    /** The retention as min age: pid 8's fresh newest snapshot gives way to its old one; fresh-only pid 9 is absent. */
    private static final List<String> ALL_OLD_ENOUGH = List.of("1@1000@-", "2@1000@DELETED", "3@1000@ACTIVE",
            "4@500@ACTIVE", "5@500@DELETED", "6@500@DELETED", "7@500@DELETED", "8@500@ACTIVE");
    private static final List<String> LIVE_OLD_ENOUGH =
            List.of("1@1000@-", "3@1000@ACTIVE", "4@500@ACTIVE", "8@500@ACTIVE");

    private static NewestPerPidFixture fixture;
    private static ActorSystem system;
    private static Materializer mat;

    @BeforeClass
    public static void startAndSeed() {
        try {
            fixture = NewestPerPidFixture.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable - skipping PostgresNewestSnapshotStreamIT", t);
        }
        system = ActorSystem.create("PostgresNewestSnapshotStreamIT");
        mat = SystemMaterializer.get(system).materializer();
        fixture.execute(SEED_SNAPSHOTS, List.of(PREFIX));
        fixture.execute("ANALYZE " + ENTITY + "_snaps", List.of());
    }

    @AfterClass
    public static void stop() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    public void withoutFieldsEveryPageSizeYieldsTheNewestRowOfEveryPidWithItsLifecycle() {
        for (final int batch : List.of(2, 3, 100)) {
            assertThat(keys(journal().getNewestSnapshotsAbove("", batch, true, Duration.ZERO, mat)))
                    .as("cleanup without an age filter, batch %d", batch).containsExactlyElementsOf(ALL);
            assertThat(keys(journal().getNewestSnapshotsAbove("", batch, false, Duration.ZERO, mat)))
                    .as("deleted excluded, batch %d", batch).containsExactlyElementsOf(LIVE);
            assertThat(keys(journal().getNewestSnapshotsAbove("", batch, true, RETENTION, mat)))
                    .as("cleanup with the retention, batch %d", batch).containsExactlyElementsOf(ALL_OLD_ENOUGH);
            assertThat(keys(journal().getNewestSnapshotsAbove("", batch, false, RETENTION, mat)))
                    .as("deleted excluded, with the retention, batch %d", batch)
                    .containsExactlyElementsOf(LIVE_OLD_ENOUGH);
        }
    }

    @Test
    public void theTwoOtherOverloadsWithoutFieldsSelectTheLiveRows() {
        // the connectivity service's connection-id retrieval: lower bound and batch size only
        assertThat(keys(journal().getNewestSnapshotsAbove("", 2, mat))).containsExactlyElementsOf(LIVE);
        // the pid-filter overload snapshot streaming uses, here without fields
        assertThat(keys(journal().getNewestSnapshotsAbove(
                SnapshotFilter.of("", "^" + PREFIX + "00000[1-4]$"), 2, mat)))
                .containsExactly("1@1000@-", "3@1000@ACTIVE", "4@500@ACTIVE");
    }

    @Test
    public void aStreamWithFieldsHoldsTheSameRowsAsOneWithout() {
        for (final boolean includeDeleted : List.of(true, false)) {
            for (final Duration minAge : List.of(Duration.ZERO, RETENTION)) {
                assertThat(keys(journal().getNewestSnapshotsAbove("", 2, includeDeleted, minAge, mat, "_revision")))
                        .as("includeDeleted %s, min age %s", includeDeleted, minAge)
                        .containsExactlyElementsOf(
                                keys(journal().getNewestSnapshotsAbove("", 2, includeDeleted, minAge, mat)));
            }
        }
    }

    @Test
    public void withoutFieldsAnEntryCarriesNoPayloadAndWithFieldsTheWholeSnapshot() {
        assertThat(entries(journal().getNewestSnapshotsAbove("", 100, true, Duration.ZERO, mat)))
                .hasSize(ALL.size())
                .allSatisfy(e -> assertThat(e.getJson()).isEqualTo(JsonObject.empty()));
        // the projection is not pushed into SQL: a stream with fields receives the whole snapshot
        assertThat(entries(journal().getNewestSnapshotsAbove("", 100, true, Duration.ZERO, mat, "_revision")))
                .hasSize(ALL.size())
                .allSatisfy(e -> {
                    assertThat(e.getJson().getValue("_revision").map(JsonValue::asLong))
                            .contains(e.getSequenceNumber().orElseThrow());
                    assertThat(e.getJson().getValue("thingId")).isPresent();
                });
    }

    private static PostgresReadJournal journal() {
        return fixture.readJournal(ENTITY);
    }

    private static List<SnapshotEntry> entries(final Source<SnapshotEntry, NotUsed> stream) {
        return PidStreamTestSupport.run(stream, mat, STREAM_TIMEOUT);
    }

    private static List<String> keys(final Source<SnapshotEntry, NotUsed> stream) {
        return entries(stream).stream()
                .map(e -> Integer.parseInt(e.getPid().orElseThrow().substring(PREFIX.length())) + "@"
                        + e.getSequenceNumber().orElseThrow() + "@" + e.getLifecycle().orElse("-"))
                .toList();
    }
}
