/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistentactors.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bson.Document;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mongodb.client.result.DeleteResult;

import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link Cleanup}.
 */
public final class CleanupTest {

    private final ActorSystem actorSystem = ActorSystem.create();

    private MongoReadJournal mongoReadJournal;
    private Materializer materializer;

    @Before
    public void init() {
        mongoReadJournal = mock(MongoReadJournal.class);
        materializer = SystemMaterializer.get(actorSystem).materializer();
    }

    @After
    public void cleanUp() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Test
    public void emptyStream() {
        when(mongoReadJournal.getNewestSnapshotsAbove(any(), anyInt(), eq(true), any(), any()))
                .thenReturn(Source.empty());

        final var underTest = new Cleanup(mongoReadJournal, materializer, () -> Pair.create(0, 1),
                Duration.ZERO, 1, 1, true);
        final var result = underTest.getCleanupStream("")
                .flatMapConcat(x -> x)
                .runWith(Sink.seq(), materializer)
                .toCompletableFuture()
                .join();
        assertThat(result).isEmpty();
    }

    @Test
    public void deleteFinalDeletedSnapshot() {
        when(mongoReadJournal.getNewestSnapshotsAbove(any(), anyInt(), eq(true), any(), any()))
                .thenReturn(Source.single(new Document().append("_id", "thing:p:id")
                        .append("__lifecycle", "DELETED")
                        .append("sn", 50L)));

        when(mongoReadJournal.getSmallestEventSeqNo(any())).thenReturn(Source.single(Optional.of(30L)));
        when(mongoReadJournal.getSmallestSnapshotSeqNo(any())).thenReturn(Source.single(Optional.of(40L)));

        // code the argument sequence numbers in the DeleteResult
        doAnswer(invocation -> Source.single(DeleteResult.acknowledged(
                invocation.<Long>getArgument(1) * 100L + invocation.<Long>getArgument(2))))
                .when(mongoReadJournal).deleteEvents(any(), anyLong(), anyLong());
        doAnswer(invocation -> Source.single(DeleteResult.acknowledged(
                invocation.<Long>getArgument(1) * 1000L + invocation.<Long>getArgument(2) * 10L)))
                .when(mongoReadJournal).deleteSnapshots(any(), anyLong(), anyLong());

        final var underTest = new Cleanup(mongoReadJournal, materializer, () -> Pair.create(0, 1),
                Duration.ZERO, 1, 4, true);

        final var result = underTest.getCleanupStream("")
                .flatMapConcat(x -> x)
                .runWith(Sink.seq(), materializer).toCompletableFuture().join();
        final var seqNrs = result.stream()
                .map(cleanupResult -> cleanupResult.result.getDeletedCount())
                .toList();
        final var types = result.stream().map(cleanupResult -> cleanupResult.type.name()).toList();

        assertThat(seqNrs).containsExactly(3033L, 3437L, 3841L, 4245L, 4649L, 39420L, 43460L, 47500L);
        assertThat(types).containsExactly("EVENTS", "EVENTS", "EVENTS", "EVENTS", "EVENTS", "SNAPSHOTS", "SNAPSHOTS",
                "SNAPSHOTS");
    }

    @Test
    public void excludeFinalDeletedSnapshot() {
        when(mongoReadJournal.getNewestSnapshotsAbove(any(), anyInt(), eq(true), any(), any()))
                .thenReturn(Source.single(new Document().append("_id", "thing:p:id")
                        .append("__lifecycle", "DELETED")
                        .append("sn", 50L)));

        when(mongoReadJournal.getSmallestEventSeqNo(any())).thenReturn(Source.single(Optional.of(30L)));
        when(mongoReadJournal.getSmallestSnapshotSeqNo(any())).thenReturn(Source.single(Optional.of(40L)));

        // code the argument sequence numbers in the DeleteResult
        doAnswer(invocation -> Source.single(DeleteResult.acknowledged(
                invocation.<Long>getArgument(1) * 100L + invocation.<Long>getArgument(2))))
                .when(mongoReadJournal).deleteEvents(any(), anyLong(), anyLong());
        doAnswer(invocation -> Source.single(DeleteResult.acknowledged(
                invocation.<Long>getArgument(1) * 1000L + invocation.<Long>getArgument(2) * 10L)))
                .when(mongoReadJournal).deleteSnapshots(any(), anyLong(), anyLong());

        final var underTest = new Cleanup(mongoReadJournal, materializer, () -> Pair.create(0, 1),
                Duration.ZERO, 1, 4, false);

        final var result = underTest.getCleanupStream("")
                .flatMapConcat(x -> x)
                .runWith(Sink.seq(), materializer).toCompletableFuture().join();
        final var seqNrs = result.stream()
                .map(cleanupResult -> cleanupResult.result.getDeletedCount())
                .toList();
        final var types = result.stream().map(cleanupResult -> cleanupResult.type.name()).toList();

        assertThat(seqNrs).containsExactly(3033L, 3437L, 3841L, 4245L, 4649L, 38410L, 42450L, 46490L);
        assertThat(types).containsExactly("EVENTS", "EVENTS", "EVENTS", "EVENTS", "EVENTS", "SNAPSHOTS", "SNAPSHOTS",
                "SNAPSHOTS");
    }

    @Test
    public void ignorePidsNotResponsibleFor() {
        when(mongoReadJournal.getNewestSnapshotsAbove(any(), anyInt(), eq(true), any(), any()))
                .thenReturn(Source.from(List.of(
                        new Document().append("_id", "thing:p:id1")
                                .append("__lifecycle", "DELETED")
                                .append("sn", 50L),
                        new Document().append("_id", "thing:p:id2")
                                .append("__lifecycle", "DELETED")
                                .append("sn", 50L),
                        new Document().append("_id", "thing:p:id3")
                                .append("__lifecycle", "DELETED")
                                .append("sn", 50L)
                )));

        when(mongoReadJournal.getSmallestEventSeqNo(any())).thenReturn(Source.single(Optional.of(30L)));
        when(mongoReadJournal.getSmallestSnapshotSeqNo(any())).thenReturn(Source.single(Optional.of(40L)));

        doAnswer(invocation -> Source.single(DeleteResult.acknowledged(
                invocation.<Long>getArgument(1) * 100L + invocation.<Long>getArgument(2))))
                .when(mongoReadJournal).deleteEvents(any(), anyLong(), anyLong());
        doAnswer(invocation -> Source.single(DeleteResult.acknowledged(
                invocation.<Long>getArgument(1) * 1000L + invocation.<Long>getArgument(2) * 10L)))
                .when(mongoReadJournal).deleteSnapshots(any(), anyLong(), anyLong());

        // WHEN: the instance is responsible for 1/3 of the 3 PIDs
        final var underTest = new Cleanup(mongoReadJournal, materializer, () -> Pair.create(2, 3),
                Duration.ZERO, 1, 4, false);

        final var result = underTest.getCleanupStream("")
                .flatMapConcat(x -> x)
                .runWith(Sink.seq(), materializer).toCompletableFuture().join();
        final var seqNrs = result.stream()
                .map(cleanupResult -> cleanupResult.result.getDeletedCount())
                .toList();
        final var types = result.stream().map(cleanupResult -> cleanupResult.type.name()).toList();
        final var pids =
                result.stream().map(cleanupResult -> cleanupResult.snapshotRevision.pid).collect(Collectors.toSet());

        // THEN: exactly 1 PID was cleaned up
        assertThat(seqNrs).containsExactly(3033L, 3437L, 3841L, 4245L, 4649L, 38410L, 42450L, 46490L);
        assertThat(types).containsExactly("EVENTS", "EVENTS", "EVENTS", "EVENTS", "EVENTS", "SNAPSHOTS", "SNAPSHOTS",
                "SNAPSHOTS");
        assertThat(pids.size()).isEqualTo(1);
    }
}
