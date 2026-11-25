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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.junit.Test;

import io.r2dbc.spi.R2dbcBadGrammarException;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import io.r2dbc.spi.R2dbcTransientResourceException;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.persistence.AtomicWrite;
import org.apache.pekko.persistence.PersistentRepr;

import reactor.core.publisher.Mono;
import reactor.pool.PoolAcquirePendingLimitException;

/**
 * Unit tests pinning the SPI-correct write-error classification of {@link PostgresJournalOps}: transient/unclassified
 * store errors must FAIL the future (actor stop + backoff restart), while serialization failures must become a
 * per-write REJECTION ({@code Optional.of}), never a synchronous throw out of {@code writeMessages}.
 */
public final class PostgresJournalOpsErrorMappingTest {

    private final PostgresPersistenceOperations operations = mock(PostgresPersistenceOperations.class);
    private final PostgresJournalOps journalOps = PostgresJournalOps.of(operations);

    private static AtomicWrite atomicWrite(final Object payload) {
        final PersistentRepr repr = PersistentRepr.apply(payload, 1L, "thing:err:x", "", false,
                ActorRef.noSender(), "writer");
        return AtomicWrite.apply(repr);
    }

    @Test
    public void transientConnectionErrorFailsTheFuture() {
        when(operations.insertEvents(any())).thenReturn(Mono.error(
                new R2dbcNonTransientResourceException("connection closed")));
        final CompletionStage<Iterable<Optional<Exception>>> result =
                journalOps.writeMessages(List.of(atomicWrite("{\"k\":1}")));
        assertThatThrownBy(() -> result.toCompletableFuture().get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(R2dbcNonTransientResourceException.class);
    }

    @Test
    public void poolPendingAcquireLimitIsClassifiedAsPoolOverloadAndFailsTheFuture() {
        // PoolAcquirePendingLimitException is not an R2dbcException; without an explicit branch it would be logged as
        // an "unclassified store error". It must fail the future (lost write -> actor stop, never a rejection) with a
        // classified pool-overload error that keeps the pool's exception as its cause.
        final PoolAcquirePendingLimitException pendingLimit = new PoolAcquirePendingLimitException(200);
        when(operations.insertEvents(any())).thenReturn(Mono.error(pendingLimit));

        final CompletionStage<Iterable<Optional<Exception>>> result =
                journalOps.writeMessages(List.of(atomicWrite("{\"k\":1}")));

        assertThatThrownBy(() -> result.toCompletableFuture().get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(PostgresPoolOverloadException.class)
                .isInstanceOf(R2dbcTransientResourceException.class)
                .hasMessageContaining("pool overload")
                .hasMessageContaining("pending-acquire-max-size=200")
                .hasCause(pendingLimit);
    }

    @Test
    public void serializationFailureIsARejectionNotAnActorStop() throws Exception {
        // A payload toInsert cannot serialize (not JsonValue/CharSequence) must become a per-write
        // REJECTION (Optional.of), NOT a synchronous throw / failed future.
        final CompletionStage<Iterable<Optional<Exception>>> result =
                journalOps.writeMessages(List.of(atomicWrite(new Object())));
        final List<Optional<Exception>> slots = new ArrayList<>();
        result.toCompletableFuture().get(5, TimeUnit.SECONDS).forEach(slots::add);
        assertThat(slots).hasSize(1);
        assertThat(slots.get(0)).isPresent();
        assertThat(slots.get(0).get()).isInstanceOf(IllegalArgumentException.class);
        verify(operations, never()).insertEvents(any());
    }

    @Test
    public void sqlStateClass22DataExceptionIsARejectionNotAnActorStop() throws Exception {
        // Ditto's JsonCharEscaper escapes U+0000 as the SIX-CHARACTER sequence (backslash, 'u', '0', '0', '0', '0'),
        // so the raw-NUL guard in toJsonText never fires; PostgreSQL then rejects the jsonb insert at COMMIT time
        // with SQLSTATE 22P05
        // ("unsupported Unicode escape sequence"), a SQL Data Exception (class 22). The r2dbc-postgresql driver's
        // ExceptionFactory maps EVERY class-22 SQLSTATE to R2dbcBadGrammarException (see
        // io.r2dbc.postgresql.ExceptionFactory#createException: case "22" falls into the same switch arm as "03"/
        // "42"/"26"), so this is deliberately an R2dbcBadGrammarException carrying sqlState "22P05" — the exact
        // concrete type/state pair a real insert throws — to pin that the SQLSTATE-22 classification runs BEFORE
        // the generic bad-grammar arm, not just before the unclassified default arm.
        when(operations.insertEvents(any())).thenReturn(Mono.error(
                new R2dbcBadGrammarException("unsupported Unicode escape sequence", "22P05", 0)));
        final CompletionStage<Iterable<Optional<Exception>>> result =
                journalOps.writeMessages(List.of(atomicWrite("{\"k\":1}")));
        final List<Optional<Exception>> slots = new ArrayList<>();
        result.toCompletableFuture().get(5, TimeUnit.SECONDS).forEach(slots::add);
        assertThat(slots).hasSize(1);
        assertThat(slots.get(0)).as("class-22 data exceptions are deterministic content errors: reject, don't stop")
                .isPresent();
        assertThat(slots.get(0).get()).isInstanceOf(R2dbcBadGrammarException.class);
    }

}
