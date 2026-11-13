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
import org.eclipse.ditto.internal.utils.persistence.postgres.payload.JsonTextPayloadCodec;
import org.eclipse.ditto.internal.utils.persistence.postgres.payload.PostgresPayloadCodec;

import org.apache.pekko.persistence.SelectedSnapshot;
import org.apache.pekko.persistence.SnapshotMetadata;
import org.apache.pekko.persistence.SnapshotSelectionCriteria;

/**
 * The plain (non-Actor) logic of {@link PostgresSnapshotStore}, extracted so it can be unit-tested without a Pekko
 * {@code ActorSystem} (the japi {@code SnapshotStore} base class forbids {@code new}). Holds the selection-criteria
 * binding, the two {@code deleteAsync} overloads and the idempotent upsert. The {@link PostgresPayloadCodec} turns
 * each snapshot into the JSON text of the {@code snapshot} column and back: JSON text as it is for the entity
 * snapshot stores, the Pekko-serialization envelope for the remember-entities store.
 */
@ThreadSafe
public final class PostgresSnapshotStoreOps {

    /** 9999-12-31T23:59:59Z — the largest {@code written_at} we bind for a {@code Long.MAX_VALUE} upper bound. */
    private static final long MAX_SAFE_TIMESTAMPTZ_MILLIS = 253402300799000L;
    private static final long MIN_EPOCH_MILLIS = 0L;

    private final PostgresPersistenceOperations operations;
    private final PostgresPayloadCodec codec;

    private PostgresSnapshotStoreOps(final PostgresPersistenceOperations operations,
            final PostgresPayloadCodec codec) {
        this.operations = operations;
        this.codec = codec;
    }

    /**
     * @param operations the shared persistence operations bound to the store's entity tables.
     * @return snapshot-store ops storing the snapshots as the JSON text they already are
     * ({@link JsonTextPayloadCodec}).
     */
    public static PostgresSnapshotStoreOps of(final PostgresPersistenceOperations operations) {
        return of(operations, JsonTextPayloadCodec.INSTANCE);
    }

    /**
     * @param operations the shared persistence operations bound to the store's entity tables.
     * @param codec turns each snapshot into the stored JSON text and back.
     * @return snapshot-store ops storing the snapshots through {@code codec}.
     */
    public static PostgresSnapshotStoreOps of(final PostgresPersistenceOperations operations,
            final PostgresPayloadCodec codec) {
        return new PostgresSnapshotStoreOps(checkNotNull(operations, "operations"), checkNotNull(codec, "codec"));
    }

    /** Loads the best snapshot honouring all four {@link SnapshotSelectionCriteria} bounds. */
    public CompletionStage<Optional<SelectedSnapshot>> loadAsync(final String persistenceId,
            final SnapshotSelectionCriteria criteria) {
        final long maxSeq = criteria.maxSequenceNr();
        final long minSeq = criteria.minSequenceNr();
        final Instant maxTs = toInstantUpper(criteria.maxTimestamp());
        final Instant minTs = toInstantLower(criteria.minTimestamp());
        return operations.loadSnapshot(persistenceId, maxSeq, minSeq, maxTs, minTs)
                .map(this::toSelectedSnapshot)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .toFuture();
    }

    /**
     * Idempotent upsert; {@code written_at} from {@code SnapshotMetadata.timestamp}, not {@code now()}.
     * <p>
     * Serialization ({@link PostgresPayloadCodec#encode(Object)} / {@link #extractLifecycle(String)}) is deferred into
     * {@code Mono.fromCallable(...)} so that an unserializable snapshot (e.g. a {@code BsonValue} the Mongo adapter
     * would produce) surfaces as a <em>failed Future</em>, which is what Pekko's {@code SnapshotStore} contract
     * expects, rather than as a synchronous throw out of {@code saveAsync} (L-13).
     */
    public CompletionStage<Void> saveAsync(final SnapshotMetadata metadata, final Object snapshot) {
        final Instant writtenAt = Instant.ofEpochMilli(metadata.timestamp());
        return reactor.core.publisher.Mono.fromCallable(() -> codec.encode(snapshot))
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

    private SelectedSnapshot toSelectedSnapshot(final SnapshotRow row) {
        final SnapshotMetadata metadata = new SnapshotMetadata(row.pid(), row.sequenceNr(),
                row.writtenAt().toEpochMilli());
        // With the JSON text codec the JSONB snapshot text is the raw payload; Pekko hands it to the snapshot adapter,
        // whose PostgresSnapshotCodec decodes it to JSON before the serializer recovers the domain object. With the
        // Pekko-serialization codec the payload is the deserialized message itself.
        return SelectedSnapshot.create(metadata, codec.decode(row.snapshotJson()));
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
