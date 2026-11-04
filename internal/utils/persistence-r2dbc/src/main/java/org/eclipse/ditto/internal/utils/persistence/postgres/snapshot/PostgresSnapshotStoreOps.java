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
package org.eclipse.ditto.internal.utils.persistence.postgres.snapshot;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.SnapshotRow;

import org.apache.pekko.persistence.SelectedSnapshot;
import org.apache.pekko.persistence.SnapshotMetadata;
import org.apache.pekko.persistence.SnapshotSelectionCriteria;

/**
 * The plain (non-Actor) logic of {@link PostgresSnapshotStore}, extracted so it can be unit-tested without a Pekko
 * {@code ActorSystem} (the japi {@code SnapshotStore} base class forbids {@code new}). Holds the selection-criteria
 * binding, the two {@code deleteAsync} overloads and the idempotent upsert.
 */
@ThreadSafe
public final class PostgresSnapshotStoreOps {

    /** 9999-12-31T23:59:59Z — the largest {@code written_at} we bind for a {@code Long.MAX_VALUE} upper bound. */
    private static final long MAX_SAFE_TIMESTAMPTZ_MILLIS = 253402300799000L;
    private static final long MIN_EPOCH_MILLIS = 0L;

    private final PostgresPersistenceOperations operations;

    private PostgresSnapshotStoreOps(final PostgresPersistenceOperations operations) {
        this.operations = operations;
    }

    public static PostgresSnapshotStoreOps of(final PostgresPersistenceOperations operations) {
        return new PostgresSnapshotStoreOps(checkNotNull(operations, "operations"));
    }

    /** Loads the best snapshot honouring all four {@link SnapshotSelectionCriteria} bounds. */
    public CompletionStage<Optional<SelectedSnapshot>> loadAsync(final String persistenceId,
            final SnapshotSelectionCriteria criteria) {
        final long maxSeq = criteria.maxSequenceNr();
        final long minSeq = criteria.minSequenceNr();
        final Instant maxTs = toInstantUpper(criteria.maxTimestamp());
        final Instant minTs = toInstantLower(criteria.minTimestamp());
        return operations.loadSnapshot(persistenceId, maxSeq, minSeq, maxTs, minTs)
                .map(PostgresSnapshotStoreOps::toSelectedSnapshot)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .toFuture();
    }

    /**
     * Idempotent upsert; {@code written_at} from {@code SnapshotMetadata.timestamp}, not {@code now()}.
     * <p>
     * Serialization ({@link #toJsonText(Object)} / {@link #extractLifecycle(String)}) is deferred into
     * {@code Mono.fromCallable(...)} so that an unserializable snapshot (e.g. a {@code BsonValue} the Mongo adapter
     * would produce) surfaces as a <em>failed Future</em>, which is what Pekko's {@code SnapshotStore} contract
     * expects, rather than as a synchronous throw out of {@code saveAsync} (L-13).
     */
    public CompletionStage<Void> saveAsync(final SnapshotMetadata metadata, final Object snapshot) {
        final Instant writtenAt = Instant.ofEpochMilli(metadata.timestamp());
        return reactor.core.publisher.Mono.fromCallable(() -> toJsonText(snapshot))
                .flatMap(snapshotJson -> operations.saveSnapshot(metadata.persistenceId(), metadata.sequenceNr(),
                        snapshotJson, extractLifecycle(snapshotJson), writtenAt))
                .<Void>then(reactor.core.publisher.Mono.empty())
                .toFuture();
    }

    /**
     * {@code deleteAsync(SnapshotMetadata)}: exact {@code (pid, sn, written_at)} when a timestamp is present;
     * Pekko's {@code Snapshotter.deleteSnapshot(seqNr)} sends timestamp 0 ("unknown") — reference plugins
     * treat that as a WILDCARD, so fall back to {@code (pid, sn)}.
     */
    public CompletionStage<Void> deleteAsync(final SnapshotMetadata metadata) {
        final reactor.core.publisher.Mono<Long> delete = metadata.timestamp() == 0L
                ? operations.deleteSnapshotsBySn(metadata.persistenceId(), metadata.sequenceNr())
                : operations.deleteSnapshotExact(metadata.persistenceId(), metadata.sequenceNr(),
                        Instant.ofEpochMilli(metadata.timestamp()));
        return delete.<Void>then(reactor.core.publisher.Mono.empty()).toFuture();
    }

    /** {@code deleteAsync(pid, criteria)}: upper-bounded {@code sn<=maxSeq AND written_at<=maxTs}. */
    public CompletionStage<Void> deleteAsync(final String persistenceId, final SnapshotSelectionCriteria criteria) {
        final long maxSeq = criteria.maxSequenceNr();
        final Instant maxTs = toInstantUpper(criteria.maxTimestamp());
        return operations.deleteSnapshotsUpTo(persistenceId, maxSeq, maxTs)
                .<Void>then(reactor.core.publisher.Mono.empty())
                .toFuture();
    }

    private static SelectedSnapshot toSelectedSnapshot(final SnapshotRow row) {
        final SnapshotMetadata metadata = new SnapshotMetadata(row.pid(), row.sequenceNr(),
                row.writtenAt().toEpochMilli());
        // The JSONB snapshot text is the raw payload; Pekko hands it to the snapshot adapter, whose
        // PostgresSnapshotCodec decodes it to JSON before the serializer recovers the domain object.
        return SelectedSnapshot.create(metadata, row.snapshotJson());
    }

    @Nullable
    private static String extractLifecycle(final String snapshotJson) {
        try {
            return org.eclipse.ditto.json.JsonFactory.newObject(snapshotJson)
                    .getValue("__lifecycle")
                    .filter(org.eclipse.ditto.json.JsonValue::isString)
                    .map(org.eclipse.ditto.json.JsonValue::asString)
                    .orElse(null);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static String toJsonText(final Object snapshot) {
        if (snapshot instanceof org.eclipse.ditto.json.JsonValue jsonValue) {
            return jsonValue.toString();
        }
        if (snapshot instanceof CharSequence charSequence) {
            return charSequence.toString();
        }
        if (snapshot instanceof byte[] bytes) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("Unable to serialize snapshot of type <" + snapshot.getClass()
                + ">; expected the PostgresSnapshotCodec's JSON text (JsonValue/String/byte[]).");
    }

    private static Instant toInstantUpper(final long epochMillis) {
        if (epochMillis == Long.MAX_VALUE) {
            return Instant.ofEpochMilli(MAX_SAFE_TIMESTAMPTZ_MILLIS);
        }
        return Instant.ofEpochMilli(epochMillis);
    }

    private static Instant toInstantLower(final long epochMillis) {
        return Instant.ofEpochMilli(Math.max(MIN_EPOCH_MILLIS, epochMillis));
    }

}
