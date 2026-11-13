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
package org.eclipse.ditto.internal.utils.persistence.postgres.readjournal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.internal.utils.persistence.api.DittoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.api.SnapshotEntry;
import org.eclipse.ditto.internal.utils.persistence.api.SnapshotFilter;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.JournalRow;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.RecordingConnectionFactory;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.SnapshotHead;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.SnapshotRow;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.StubClientSupport;
import org.eclipse.ditto.json.JsonObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.persistence.query.EventEnvelope;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.persistence.query.Sequence;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

/**
 * Contract test for {@link PostgresReadJournal}: every one of the {@link DittoReadJournal} abstract
 * methods has an explicit disposition (SQL / no-op / documented stub) and NONE throws
 * {@code UnsupportedOperationException} (or any other exception) at runtime when invoked with representative arguments
 * against a stubbed client. The four {@code ensure*Index} methods are no-ops that complete successfully.
 */
public final class PostgresReadJournalContractTest {

    private static ActorSystem system;
    private static Materializer mat;

    @BeforeClass
    public static void beforeClass() {
        system = ActorSystem.create("PostgresReadJournalContractTest");
        mat = SystemMaterializer.get(system).materializer();
    }

    @AfterClass
    public static void afterClass() {
        if (system != null) {
            system.terminate();
        }
    }

    private PostgresReadJournal newReadJournal() {
        final DittoPostgresClient client = StubClientSupport.clientFor(new RecordingConnectionFactory());
        return PostgresReadJournal.of(PostgresPersistenceOperations.of(client, "things"));
    }

    @Test
    public void everyDittoReadJournalAbstractMethodIsImplemented() {
        // No abstract method of DittoReadJournal (or its 6 Pekko query super-SPIs) may remain unimplemented.
        final List<Method> unimplemented = new ArrayList<>();
        for (final Method method : DittoReadJournal.class.getMethods()) {
            if (java.lang.reflect.Modifier.isAbstract(method.getModifiers())) {
                try {
                    PostgresReadJournal.class.getMethod(method.getName(), method.getParameterTypes());
                } catch (final NoSuchMethodException e) {
                    unimplemented.add(method);
                }
            }
        }
        assertThat(unimplemented).as("unimplemented DittoReadJournal methods").isEmpty();
    }

    @Test
    public void noMethodThrowsAtRuntime() {
        final PostgresReadJournal journal = newReadJournal();

        // Index management: no-ops that complete with Done.
        assertCompletes(journal.ensureTagPidIndex());
        assertCompletes(journal.ensureSnapshotCollectionPidIdIndex());
        assertCompletes(journal.ensureSnapshotCollectionPidSnIndex());
        assertCompletes(journal.ensureSnapshotCollectionPidSnIdIndex());

        // PID retrieval.
        drain(journal.getJournalPids(10, Duration.ofSeconds(1), mat));
        drain(journal.getLatestJournalEntries(10, Duration.ofSeconds(1), mat));
        drain(journal.getJournalPidsWithTag("always-alive", 10, Duration.ofSeconds(1), mat, true));
        drain(journal.getJournalPidsWithTagOrderedByPriorityTag("always-alive", Duration.ofSeconds(1)));
        drain(journal.getJournalPidsAbove("", 10, mat));
        drain(journal.getMostRecentJournalTagsForPid("thing:ns:id"));
        drain(journal.getJournalPidsAboveWithTag("", "always-alive", 10, mat));

        // Snapshot retrieval.
        drain(journal.getLastSnapshotSequenceNumberBeforeTimestamp("thing:ns:id", Instant.now()));
        drain(journal.getNewestSnapshotsAbove("", 10, mat));
        drain(journal.getNewestSnapshotsAbove("", 10, true, Duration.ofDays(3), mat));
        drain(journal.getNewestSnapshotsAbove(SnapshotFilter.of("", Duration.ZERO), 10, mat));
        drain(journal.getSmallestEventSeqNo("thing:ns:id"));
        drain(journal.getLatestEventSeqNo("thing:ns:id"));
        drain(journal.getSmallestSnapshotSeqNo("thing:ns:id"));

        // Deletion.
        drain(journal.deleteEvents("thing:ns:id", 1L, 10L));
        drain(journal.deleteSnapshots("thing:ns:id", 1L, 10L));

        // Pekko ReadJournal SPI.
        drain(journal.currentEventsByPersistenceId("thing:ns:id", 0L, Long.MAX_VALUE));
        drain(journal.currentEventsByTag("always-alive", Offset.noOffset()));
        drain(journal.currentPersistenceIds());
        drain(journal.eventsByPersistenceId("thing:ns:id", 0L, Long.MAX_VALUE));
        drain(journal.eventsByTag("always-alive", Offset.sequence(0L)));
        drain(journal.persistenceIds());

        // Event payload mapping.
        final org.apache.pekko.persistence.query.EventEnvelope envelope =
                org.apache.pekko.persistence.query.EventEnvelope.apply(Offset.sequence(1L), "thing:ns:id", 1L,
                        "{\"foo\":\"bar\"}");
        assertThatCode(() -> assertThat(journal.toEventJson(envelope).getValue("foo")).isPresent())
                .doesNotThrowAnyException();
    }

    @Test
    public void currentEventsByTagMapsRowToEventEnvelope() {
        // Stub a representative journal row on the MOCK operations and assert the mapped EventEnvelope values:
        // PostgresReadJournal.toEventEnvelope builds Offset.sequence(seq), pid, sn and keeps the raw JSONB text as event.
        final PostgresPersistenceOperations operations = mock(PostgresPersistenceOperations.class);
        final String eventJson = "{\"foo\":\"bar\"}";
        final JournalRow row = new JournalRow("thing:ns:id", 7L, 42L,
                "org.example.ThingEvent", List.of("always-alive", "priority-3"), eventJson);
        // Offset.noOffset() -> read journal binds 0L as the exclusive offset.
        when(operations.eventsByTag(eq("always-alive"), eq(0L)))
                .thenReturn(Source.single(row));

        final PostgresReadJournal journal = PostgresReadJournal.of(operations);
        final List<EventEnvelope> envelopes = collect(journal.currentEventsByTag("always-alive", Offset.noOffset()));

        assertThat(envelopes).hasSize(1);
        final EventEnvelope envelope = envelopes.get(0);
        assertThat(envelope.offset()).isEqualTo(Offset.sequence(42L));
        assertThat(((Sequence) envelope.offset()).value()).isEqualTo(42L);
        assertThat(envelope.persistenceId()).isEqualTo("thing:ns:id");
        assertThat(envelope.sequenceNr()).isEqualTo(7L);
        // The payload is kept as the raw JSONB text; toEventJson re-parses it to a JsonObject.
        assertThat(envelope.event()).isEqualTo(eventJson);
        assertThat(journal.toEventJson(envelope).getValue("foo")).contains(
                org.eclipse.ditto.json.JsonValue.of("bar"));
    }

    @Test
    public void withoutFieldsTheStreamReadsTheSnapshotHeadsAndCarriesAnEmptyPayload() {
        // a stream without snapshot fields (Cleanup, connection-id retrieval) runs the payload-free statement and
        // builds its entries without a payload to parse
        final PostgresPersistenceOperations operations = mock(PostgresPersistenceOperations.class);
        final SnapshotHead head =
                new SnapshotHead("thing:ns:id", 12L, "DELETED", Instant.parse("2026-06-16T10:15:30Z"));
        when(operations.getNewestSnapshotHeadsAbove(eq(""), eq(10), eq(Duration.ZERO), eq("")))
                .thenReturn(Source.single(head));
        when(operations.getNewestSnapshotHeadsAbove(eq("thing:ns:id"), eq(10), eq(Duration.ZERO), eq("")))
                .thenReturn(Source.empty());

        final List<SnapshotEntry> entries =
                collect(PostgresReadJournal.of(operations).getNewestSnapshotsAbove("", 10, true, Duration.ZERO, mat));

        assertThat(entries).hasSize(1);
        final SnapshotEntry entry = entries.get(0);
        assertThat(entry.getPid()).contains("thing:ns:id");
        assertThat(entry.getSequenceNumber()).hasValue(12L);
        assertThat(entry.getLifecycle()).contains("DELETED");
        assertThat(entry.isDeleted()).isTrue();
        assertThat(entry.getJson()).isEqualTo(JsonObject.empty());
        verify(operations, never()).getNewestSnapshotsAbove(anyString(), anyInt(), any(Duration.class), anyString());
    }

    @Test
    public void withFieldsTheStreamReadsAndParsesThePayload() {
        final PostgresPersistenceOperations operations = mock(PostgresPersistenceOperations.class);
        final String snapshotJson = "{\"attributes\":{\"location\":\"kitchen\"}}";
        final SnapshotRow row = new SnapshotRow("thing:ns:id", 12L, "DELETED",
                Instant.parse("2026-06-16T10:15:30Z"), snapshotJson);
        when(operations.getNewestSnapshotsAbove(eq(""), eq(10), eq(Duration.ZERO), eq("")))
                .thenReturn(Source.single(row));
        when(operations.getNewestSnapshotsAbove(eq("thing:ns:id"), eq(10), eq(Duration.ZERO), eq("")))
                .thenReturn(Source.empty());

        final List<SnapshotEntry> entries = collect(PostgresReadJournal.of(operations)
                .getNewestSnapshotsAbove("", 10, true, Duration.ZERO, mat, "attributes"));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getSequenceNumber()).hasValue(12L);
        assertThat(entries.get(0).isDeleted()).isTrue();
        assertThat(entries.get(0).getJson()).isEqualTo(org.eclipse.ditto.json.JsonFactory.newObject(snapshotJson));
        verify(operations, never()).getNewestSnapshotHeadsAbove(anyString(), anyInt(), any(Duration.class),
                anyString());
    }

    @Test
    public void everyOverloadRoutesOnItsSnapshotFields() {
        final PostgresPersistenceOperations operations = mock(PostgresPersistenceOperations.class);
        when(operations.getNewestSnapshotHeadsAbove(anyString(), anyInt(), any(Duration.class), anyString()))
                .thenReturn(Source.empty());
        when(operations.getNewestSnapshotsAbove(anyString(), anyInt(), any(Duration.class), anyString()))
                .thenReturn(Source.empty());
        final PostgresReadJournal journal = PostgresReadJournal.of(operations);

        drain(journal.getNewestSnapshotsAbove("", 10, mat));
        drain(journal.getNewestSnapshotsAbove("", 10, true, Duration.ofDays(3), mat));
        drain(journal.getNewestSnapshotsAbove(SnapshotFilter.of("", "^thing:"), 10, mat));
        drain(journal.getNewestSnapshotsAbove("", 10, false, Duration.ZERO, mat, (String[]) null));
        verify(operations, times(4)).getNewestSnapshotHeadsAbove(anyString(), anyInt(), any(Duration.class),
                anyString());
        verify(operations, never()).getNewestSnapshotsAbove(anyString(), anyInt(), any(Duration.class), anyString());

        drain(journal.getNewestSnapshotsAbove("", 10, mat, "_revision"));
        drain(journal.getNewestSnapshotsAbove("", 10, true, Duration.ofDays(3), mat, "_revision"));
        drain(journal.getNewestSnapshotsAbove(SnapshotFilter.of("", "^thing:"), 10, mat, "_revision", "policyId"));
        verify(operations, times(3)).getNewestSnapshotsAbove(anyString(), anyInt(), any(Duration.class), anyString());
        verify(operations, times(4)).getNewestSnapshotHeadsAbove(anyString(), anyInt(), any(Duration.class),
                anyString());
    }

    @Test
    public void getLatestEventSeqNoPassesThroughTheHighWaterMark() {
        // getLatestEventSeqNo is a straight pass-through of the high-water-mark Optional from the operations layer.
        final PostgresPersistenceOperations operations = mock(PostgresPersistenceOperations.class);
        when(operations.getLatestEventSeqNo(eq("thing:ns:id")))
                .thenReturn(Source.single(Optional.of(99L)));

        final PostgresReadJournal journal = PostgresReadJournal.of(operations);
        final List<Optional<Long>> result = collect(journal.getLatestEventSeqNo("thing:ns:id"));

        assertThat(result).containsExactly(Optional.of(99L));
    }

    @Test
    public void getLatestEventSeqNoPassesThroughEmptyForUnknownPid() {
        // An unknown pid yields Optional.empty() from the operations layer and is passed through unchanged.
        final PostgresPersistenceOperations operations = mock(PostgresPersistenceOperations.class);
        when(operations.getLatestEventSeqNo(eq("thing:ns:unknown")))
                .thenReturn(Source.single(Optional.empty()));

        final PostgresReadJournal journal = PostgresReadJournal.of(operations);
        final List<Optional<Long>> result = collect(journal.getLatestEventSeqNo("thing:ns:unknown"));

        assertThat(result).containsExactly(Optional.empty());
    }

    private <T> List<T> collect(final Source<T, NotUsed> source) {
        try {
            return source.runWith(Sink.seq(), system).toCompletableFuture().get(10, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("Failed to collect source", e);
        }
    }

    private <T> void drain(final Source<T, ?> source) {
        assertThatCode(() -> source.runWith(Sink.ignore(), system)
                .toCompletableFuture().get(10, TimeUnit.SECONDS)).doesNotThrowAnyException();
    }

    private void assertCompletes(final CompletionStage<Done> stage) {
        assertThatCode(() -> assertThat(stage.toCompletableFuture().get(10, TimeUnit.SECONDS))
                .isEqualTo(Done.getInstance())).doesNotThrowAnyException();
    }

}
