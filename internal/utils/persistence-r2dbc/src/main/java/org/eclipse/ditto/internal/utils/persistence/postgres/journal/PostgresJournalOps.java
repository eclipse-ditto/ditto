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
package org.eclipse.ditto.internal.utils.persistence.postgres.journal;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.internal.utils.persistence.postgres.ops.JournalRow;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.payload.JsonTextPayloadCodec;
import org.eclipse.ditto.internal.utils.persistence.postgres.payload.PostgresPayloadCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.r2dbc.spi.R2dbcBadGrammarException;
import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.R2dbcTimeoutException;

import org.apache.pekko.persistence.AtomicWrite;
import org.apache.pekko.persistence.PersistentRepr;
import org.apache.pekko.persistence.journal.Tagged;

import reactor.pool.PoolAcquirePendingLimitException;
import scala.jdk.javaapi.CollectionConverters;

/**
 * The plain (non-Actor) write/recovery logic of {@link PostgresJournal}, extracted so it can be unit-tested without a
 * Pekko {@code ActorSystem} (the japi {@code AsyncWriteJournal} base class forbids {@code new}). {@link PostgresJournal}
 * is a thin japi adapter that delegates to this class and converts the {@link CompletionStage}s to Scala futures.
 * <p>
 * Holds the high-water-mark, idempotency and lost-write contracts described on {@link PostgresJournal}. The
 * {@link PostgresPayloadCodec} turns each event into the JSON text of the {@code event} column and back: JSON text
 * as it is for the entity journals, the Pekko-serialization envelope for the remember-entities store.
 * </p>
 */
@ThreadSafe
public final class PostgresJournalOps {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresJournalOps.class);

    /**
     * PostgreSQL SQLSTATE {@code 23505} = {@code unique_violation}. Only a genuine primary-key / unique collision can
     * be a commit-then-retry idempotency case; every other integrity violation (NOT-NULL {@code 23502}, FK
     * {@code 23503}, CHECK {@code 23514}, exclusion {@code 23P01}, …) is a constraint/schema fault that must stop the
     * actor rather than be silently dropped as a recoverable rejection.
     */
    private static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

    /**
     * PostgreSQL SQLSTATE class {@code 22} = {@code data_exception}: deterministic, content-driven errors (e.g.
     * {@code 22P05} "untranslatable character" / unsupported Unicode escape sequence, {@code 22021}
     * "character_not_in_repertoire", {@code 22023} "invalid_parameter_value", …). Retrying the exact same bytes can
     * never succeed, so these are classified as a per-write rejection rather than a lost-write actor stop. Note: the
     * r2dbc-postgresql driver's {@code ExceptionFactory} maps EVERY class-22 SQLSTATE to
     * {@link R2dbcBadGrammarException} (the same switch arm as syntax errors), so this check MUST run before the
     * {@code R2dbcBadGrammarException} arm below, not after it.
     */
    private static final String SQLSTATE_DATA_EXCEPTION_CLASS_PREFIX = "22";

    private final PostgresPersistenceOperations operations;
    private final PostgresPayloadCodec codec;

    private PostgresJournalOps(final PostgresPersistenceOperations operations, final PostgresPayloadCodec codec) {
        this.operations = operations;
        this.codec = codec;
    }

    /**
     * @param operations the shared persistence operations bound to the journal's entity tables.
     * @return journal ops storing the events as the JSON text they already are ({@link JsonTextPayloadCodec}).
     */
    public static PostgresJournalOps of(final PostgresPersistenceOperations operations) {
        return of(operations, JsonTextPayloadCodec.INSTANCE);
    }

    /**
     * @param operations the shared persistence operations bound to the journal's entity tables.
     * @param codec turns each event into the stored JSON text and back.
     * @return journal ops storing the events through {@code codec}.
     */
    public static PostgresJournalOps of(final PostgresPersistenceOperations operations,
            final PostgresPayloadCodec codec) {
        return new PostgresJournalOps(checkNotNull(operations, "operations"), checkNotNull(codec, "codec"));
    }

    /**
     * Writes each {@code AtomicWrite} in its own transaction, returning one {@code Optional<Exception>} slot per write.
     */
    public CompletionStage<Iterable<Optional<Exception>>> writeMessages(final Iterable<AtomicWrite> messages) {
        final List<CompletionStage<Optional<Exception>>> perAtomicWrite = new ArrayList<>();
        for (final AtomicWrite atomicWrite : messages) {
            perAtomicWrite.add(writeAtomic(atomicWrite));
        }
        return sequence(perAtomicWrite);
    }

    private CompletionStage<Optional<Exception>> writeAtomic(final AtomicWrite atomicWrite) {
        final List<PostgresPersistenceOperations.JournalInsert> inserts = new ArrayList<>();
        try {
            for (final PersistentRepr repr : CollectionConverters.asJava(atomicWrite.payload())) {
                inserts.add(toInsert(repr));
            }
        } catch (final IllegalArgumentException serializationFailure) {
            // Pekko SPI: serialization failures ARE the rejection case — the message is not persisted,
            // the actor continues, and onPersistRejected logs it. Covers unbound payload types and
            // payloads PostgreSQL jsonb cannot store (e.g. U+0000), where Mongo/BSON would accept them.
            LOGGER.warn("Rejecting unserializable journal write for pid <{}>: {}",
                    atomicWrite.persistenceId(), serializationFailure.getMessage());
            return CompletableFuture.completedFuture(Optional.of(serializationFailure));
        }
        return operations.insertEvents(inserts)
                .toFuture()
                .<Optional<Exception>>thenApply(ignored -> Optional.<Exception>empty())
                .exceptionallyCompose(throwable -> recoverWrite(atomicWrite, throwable));
    }

    private CompletionStage<Optional<Exception>> recoverWrite(final AtomicWrite atomicWrite,
            final Throwable throwable) {
        final Throwable cause = unwrap(throwable);
        if (cause instanceof R2dbcDataIntegrityViolationException integrityViolation) {
            if (SQLSTATE_UNIQUE_VIOLATION.equals(integrityViolation.getSqlState())) {
                // Genuine (pid, sn) PK/unique collision: the only case that may be an idempotent commit-then-retry.
                return reconcileDuplicate(atomicWrite, integrityViolation);
            }
            // Any other integrity violation (NOT-NULL / FK / CHECK / exclusion) is a fatal constraint fault, NOT a
            // duplicate: a failed Future -> WriteMessageFailure -> the actor stops, never a recoverable rejection.
            LOGGER.error("PostgreSQL write hit a non-duplicate integrity violation (SQLSTATE <{}>); treating as a "
                    + "lost write that stops the actor.", integrityViolation.getSqlState(), integrityViolation);
            return CompletableFuture.failedFuture(integrityViolation);
        }
        if (cause instanceof R2dbcException r2dbcException
                && isSqlDataException(r2dbcException)) {
            // SQLSTATE class 22 (data_exception): a deterministic content error PostgreSQL rejected the exact bytes
            // for (e.g. 22P05 when a JSON string value contains the ESCAPED U+0000 sequence Ditto's JsonCharEscaper
            // emits -- the raw-NUL guard in JsonTextPayloadCodec never sees it). Retrying the identical write can never
            // succeed, so this is a per-write REJECTION (the actor continues; Pekko calls onPersistRejected), never
            // a failed Future -> actor-stop -> backoff-restart crash loop.
            LOGGER.warn("Rejecting PostgreSQL journal write for pid <{}> due to a SQLSTATE class-22 data exception "
                            + "(SQLSTATE <{}>): deterministic content error, retrying cannot succeed.",
                    atomicWrite.persistenceId(), r2dbcException.getSqlState(), r2dbcException);
            return CompletableFuture.completedFuture(Optional.of(asException(r2dbcException)));
        }
        if (cause instanceof R2dbcTimeoutException) {
            // Pool acquire-timeout = lost write -> failed Future -> actor stop, not backpressure.
            LOGGER.error("PostgreSQL write timed out (pool acquire); treating as a lost write that stops the actor.",
                    cause);
            return CompletableFuture.failedFuture(cause);
        }
        if (cause instanceof PoolAcquirePendingLimitException pendingLimit) {
            // Pool overload: every connection is busy and the bounded pending-acquire queue is full, so the pool shed
            // this borrow at once instead of letting it queue past the caller's deadline. Same outcome as an acquire
            // timeout (lost write -> failed Future -> actor stop, never a rejection), but classified, so it is not
            // reported as an unclassified store error below. Logged without a stack trace: under overload many writes
            // are shed at once, and Pekko's own persist-failure log carries the cause.
            final PostgresPoolOverloadException overload = PostgresPoolOverloadException.of(pendingLimit);
            LOGGER.error("Shedding journal write for pid <{}> as a lost write that stops the actor: {}",
                    atomicWrite.persistenceId(), overload.getMessage());
            return CompletableFuture.failedFuture(overload);
        }
        if (cause instanceof R2dbcBadGrammarException) {
            return CompletableFuture.failedFuture(cause);
        }
        // Pekko AsyncWriteJournal SPI: "Data store connection problems must not be signaled as
        // rejections." Every unclassified store-side error (connection reset, pool disposed,
        // transient resource failures) is a lost write -> failed Future -> WriteMessageFailure ->
        // the actor stops and backoff-restarts, exactly like the Mongo plugin. Rejections are
        // reserved for deterministic, content-driven failures: serialization failures (classified in
        // writeAtomic before the insert is attempted) and SQLSTATE class-22 data exceptions (classified
        // above) -- both cases where retrying the identical write can never succeed.
        LOGGER.error("PostgreSQL write failed with an unclassified store error; treating as a lost "
                + "write that stops the actor.", cause);
        return CompletableFuture.failedFuture(asException(cause));
    }

    /**
     * Reconciles a genuine {@code (pid, sn)} unique-violation. The ONLY non-fatal outcome is the truly-idempotent
     * commit-then-blip retry, where every event's stored payload + manifest already match the retried write: that
     * yields an empty {@code Optional} (a successful, no-op write). Every other outcome is fatal (a failed Future ->
     * {@code WriteMessageFailure} -> the actor stops):
     * <ul>
     *     <li>a stored row whose payload/manifest <em>differs</em> is split-brain corruption (double-writer);</li>
     *     <li>a unique-violation with <em>no</em> matching stored row (e.g. a non-{@code (pid, sn)} unique index)
     *     is not an idempotent duplicate and must not be swallowed.</li>
     * </ul>
     */
    private CompletionStage<Optional<Exception>> reconcileDuplicate(final AtomicWrite atomicWrite,
            final R2dbcDataIntegrityViolationException duplicate) {
        final List<PersistentRepr> reprs = new ArrayList<>(CollectionConverters.asJava(atomicWrite.payload()));
        CompletionStage<Optional<Exception>> result = CompletableFuture.completedFuture(Optional.empty());
        for (final PersistentRepr repr : reprs) {
            final PostgresPersistenceOperations.JournalInsert expected = toInsert(repr);
            result = result.thenCompose(prior -> {
                if (prior.isPresent()) {
                    return CompletableFuture.completedFuture(prior);
                }
                return operations.findEvent(expected.pid(), expected.sequenceNr())
                        .<Optional<Exception>>handle((existing, sink) -> {
                            if (existing == null) {
                                // Mono.handle never emits null; a missing row arrives via the empty completion below.
                                return;
                            }
                            if (matches(existing, expected)) {
                                sink.next(Optional.empty());
                            } else {
                                sink.error(new IllegalStateException(
                                        "Duplicate sequence number " + expected.sequenceNr() + " for pid <"
                                                + expected.pid() + "> with a DIFFERENT payload/manifest: "
                                                + duplicate.getMessage()));
                            }
                        })
                        .switchIfEmpty(reactor.core.publisher.Mono.error(new IllegalStateException(
                                "Unique violation for pid <" + expected.pid() + "> sn " + expected.sequenceNr()
                                        + " with no matching stored row; not an idempotent duplicate: "
                                        + duplicate.getMessage(), duplicate)))
                        .toFuture();
            });
        }
        return result;
    }

    /** Deletes journal events {@code sn <= toSequenceNr}, moving {@code deleted_to} only and leaving {@code highest_sn} untouched. */
    public CompletionStage<Void> deleteMessagesTo(final String persistenceId, final long toSequenceNr) {
        return operations.deleteMessagesTo(persistenceId, toSequenceNr).<Void>then(reactor.core.publisher.Mono.empty())
                .toFuture();
    }

    /**
     * Reads the highest sequence number via the persisted high-water-mark; never bare {@code MAX(sn)}. The result is
     * floored at {@code fromSequenceNr} so a caller's recovery floor is never undercut by a lower stored watermark.
     */
    public CompletionStage<Long> readHighestSequenceNr(final String persistenceId, final long fromSequenceNr) {
        return operations.readHighestSequenceNr(persistenceId)
                .map(hwm -> Math.max(fromSequenceNr, hwm))
                .toFuture();
    }

    /** Replays journal events in {@code (pid, sn)} order, invoking {@code replayCallback} per row. */
    public CompletionStage<Void> replayMessages(final String persistenceId, final long fromSequenceNr,
            final long toSequenceNr, final long max, final Consumer<PersistentRepr> replayCallback) {
        return operations.replayEvents(persistenceId, fromSequenceNr, toSequenceNr, max)
                .doOnNext(row -> replayCallback.accept(toRepr(row)))
                .then()
                .toFuture();
    }

    private static boolean matches(final JournalRow existing, final PostgresPersistenceOperations.JournalInsert expected) {
        return Objects.equals(existing.manifest(), expected.manifest())
                && jsonEquals(existing.eventJson(), expected.eventJson());
    }

    private static boolean jsonEquals(final String storedJson, final String expectedJson) {
        try {
            return org.eclipse.ditto.json.JsonFactory.newObject(storedJson)
                    .equals(org.eclipse.ditto.json.JsonFactory.newObject(expectedJson));
        } catch (final RuntimeException e) {
            return Objects.equals(storedJson, expectedJson);
        }
    }

    private PostgresPersistenceOperations.JournalInsert toInsert(final PersistentRepr repr) {
        final Object payload = repr.payload();
        final Object event;
        final Set<String> tags;
        if (payload instanceof Tagged tagged) {
            event = tagged.payload();
            tags = CollectionConverters.asJava(tagged.tags());
        } else {
            event = payload;
            tags = Set.of();
        }
        return new PostgresPersistenceOperations.JournalInsert(repr.persistenceId(), repr.sequenceNr(), repr.manifest(),
                List.copyOf(tags), codec.encode(event));
    }

    private PersistentRepr toRepr(final JournalRow row) {
        return PersistentRepr.apply(codec.decode(row.eventJson()), row.sequenceNr(), row.pid(),
                row.manifest() == null ? PersistentRepr.Undefined() : row.manifest(),
                false, org.apache.pekko.actor.ActorRef.noSender(), PersistentRepr.Undefined());
    }

    private static <T> CompletionStage<Iterable<T>> sequence(final List<CompletionStage<T>> stages) {
        CompletionStage<List<T>> acc = CompletableFuture.completedFuture(new ArrayList<>());
        for (final CompletionStage<T> stage : stages) {
            acc = acc.thenCombine(stage, (list, value) -> {
                list.add(value);
                return list;
            });
        }
        return acc.thenApply(list -> (Iterable<T>) list);
    }

    private static Throwable unwrap(final Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static Exception asException(final Throwable throwable) {
        return throwable instanceof Exception exception ? exception : new RuntimeException(throwable);
    }

    private static boolean isSqlDataException(final R2dbcException r2dbcException) {
        final String sqlState = r2dbcException.getSqlState();
        return sqlState != null && sqlState.startsWith(SQLSTATE_DATA_EXCEPTION_CLASS_PREFIX);
    }

}
