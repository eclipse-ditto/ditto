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

import static org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal.LIFECYCLE;
import static org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal.S_ID;
import static org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal.S_SN;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;

import akka.NotUsed;
import akka.japi.Pair;
import akka.stream.Materializer;
import akka.stream.javadsl.Source;

/**
 * An Akka stream to handle background cleanup regulated by insert times.
 */
final class Cleanup {

    private final MongoReadJournal readJournal;
    private final Materializer materializer;
    private final Supplier<Pair<Integer, Integer>> responsibilitySupplier;
    private final Duration historyRetentionDuration;
    private final int readBatchSize;
    private final int deleteBatchSize;
    private final boolean deleteFinalDeletedSnapshot;

    Cleanup(final MongoReadJournal readJournal,
            final Materializer materializer,
            final Supplier<Pair<Integer, Integer>> responsibilitySupplier,
            final Duration historyRetentionDuration,
            final int readBatchSize,
            final int deleteBatchSize,
            final boolean deleteFinalDeletedSnapshot) {

        this.readJournal = readJournal;
        this.materializer = materializer;
        this.responsibilitySupplier = responsibilitySupplier;
        this.historyRetentionDuration = historyRetentionDuration;
        this.readBatchSize = readBatchSize;
        this.deleteBatchSize = deleteBatchSize;
        this.deleteFinalDeletedSnapshot = deleteFinalDeletedSnapshot;
    }

    static Cleanup of(final CleanupConfig config,
            final MongoReadJournal readJournal,
            final Materializer materializer,
            final Supplier<Pair<Integer, Integer>> responsibilitySupplier) {

        return new Cleanup(readJournal, materializer, responsibilitySupplier,
                config.getHistoryRetentionDuration(),
                config.getReadsPerQuery(),
                config.getWritesPerCredit(),
                config.shouldDeleteFinalDeletedSnapshot()
        );
    }

    Source<Source<CleanupResult, NotUsed>, NotUsed> getCleanupStream(final String lowerBound) {
        return getSnapshotRevisions(lowerBound).flatMapConcat(sr -> cleanUpEvents(sr).concat(cleanUpSnapshots(sr)));
    }

    private Source<SnapshotRevision, NotUsed> getSnapshotRevisions(final String lowerBound) {
        return readJournal.getNewestSnapshotsAbove(lowerBound, readBatchSize, true, historyRetentionDuration, materializer)
                .map(document -> new SnapshotRevision(document.getString(S_ID),
                        document.getLong(S_SN),
                        "DELETED".equals(document.getString(LIFECYCLE))))
                .filter(this::isMyResponsibility);
    }

    private boolean isMyResponsibility(final SnapshotRevision sr) {
        final var responsibility = responsibilitySupplier.get();
        final int denominator = responsibility.second();
        final int remainder = responsibility.first();
        final int hashCode = sr.pid.hashCode();
        final int nonNegativeHashCode = hashCode == Integer.MIN_VALUE ? 0 : hashCode < 0 ? -hashCode : hashCode;
        return nonNegativeHashCode % denominator == remainder;
    }

    private Source<Source<CleanupResult, NotUsed>, NotUsed> cleanUpEvents(final SnapshotRevision sr) {
        // leave 1 event for each snapshot to store the "always alive" tag
        return readJournal.getSmallestEventSeqNo(sr.pid).flatMapConcat(minSnOpt -> {
            if (minSnOpt.isEmpty() || minSnOpt.orElseThrow() >= sr.sn) {
                return Source.empty();
            } else {
                final List<Long> upperBounds = getSnUpperBoundsPerBatch(minSnOpt.orElseThrow(), sr.sn);
                return Source.from(upperBounds).map(upperBound -> Source.lazySource(() ->
                        readJournal
                                .deleteEvents(sr.pid, upperBound - deleteBatchSize + 1, upperBound)
                                .map(result -> new CleanupResult(CleanupResult.Type.EVENTS, sr, result))
                ).mapMaterializedValue(ignored -> NotUsed.getInstance()));
            }
        });
    }

    private Source<Source<CleanupResult, NotUsed>, NotUsed> cleanUpSnapshots(final SnapshotRevision sr) {
        return readJournal.getSmallestSnapshotSeqNo(sr.pid).flatMapConcat(minSnOpt -> {
            if (minSnOpt.isEmpty() || (minSnOpt.orElseThrow() >= sr.sn && !deleteFinalDeletedSnapshot)) {
                return Source.empty();
            } else {
                final long maxSnToDelete = deleteFinalDeletedSnapshot && sr.isDeleted ? sr.sn + 1 : sr.sn;
                final List<Long> upperBounds = getSnUpperBoundsPerBatch(minSnOpt.orElseThrow(), maxSnToDelete);
                return Source.from(upperBounds).map(upperBound -> Source.lazySource(() ->
                        readJournal.deleteSnapshots(sr.pid, upperBound - deleteBatchSize + 1, upperBound)
                                .map(result -> new CleanupResult(CleanupResult.Type.SNAPSHOTS, sr, result))
                ).mapMaterializedValue(ignored -> NotUsed.getInstance()));
            }
        });
    }

    private List<Long> getSnUpperBoundsPerBatch(final long minSn, final long snapshotRevisionSn) {
        final long difference = snapshotRevisionSn - minSn;
        // number of batches = ceil(difference / deleteBatchSize) as real numbers
        final long batches = (difference / deleteBatchSize) + (difference % deleteBatchSize == 0L ? 0L : 1L);
        final long firstBatchSn = snapshotRevisionSn - 1 - ((batches - 1) * deleteBatchSize);
        return LongStream.range(0, batches)
                .mapToObj(multiplier -> firstBatchSn + multiplier * deleteBatchSize)
                .toList();
    }
}
