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
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.payload.JsonTextPayloadCodec;
import org.junit.Before;
import org.junit.Test;

import io.r2dbc.spi.R2dbcDataIntegrityViolationException;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.persistence.AtomicWrite;
import org.apache.pekko.persistence.PersistentRepr;

import reactor.core.publisher.Mono;

/**
 * Unit tests pinning the write order of {@link PostgresJournalOps#writeMessages(Iterable)}: the atomic writes of one
 * call run one after the other in their input order, a failed write stops the later ones, a rejected write does not,
 * and a later write is not started once the call timeout has passed since the call began.
 * <p>
 * Each insert is answered by a future the test completes by hand, keyed by {@code pid:sn}; an insert without such a
 * future succeeds at once. A write that must not start therefore would succeed if it were started.
 * </p>
 */
public final class PostgresJournalOpsWriteOrderTest {

    private static final Duration GENEROUS_TIMEOUT = Duration.ofSeconds(30);
    private static final String SERIALIZABLE = "{\"k\":1}";

    private final Map<String, CompletableFuture<Void>> futures = new HashMap<>();
    private final List<String> calls = Collections.synchronizedList(new ArrayList<>());
    private PostgresPersistenceOperations operations;

    @Before
    public void stubInserts() {
        operations = mock(PostgresPersistenceOperations.class);
        when(operations.insertEvents(any())).thenAnswer(invocation -> {
            final List<PostgresPersistenceOperations.JournalInsert> inserts = invocation.getArgument(0);
            final String key = inserts.get(0).pid() + ":" + inserts.get(0).sequenceNr();
            calls.add(key);
            final CompletableFuture<Void> future = futures.get(key);
            return future == null ? Mono.empty() : Mono.fromFuture(future);
        });
    }

    @Test
    public void secondWriteStartsAfterTheFirstHasCompleted() throws Exception {
        final CompletableFuture<Void> first = handCompleted("thing:a", 1);
        final CompletableFuture<Void> second = handCompleted("thing:a", 2);

        final CompletableFuture<Iterable<Optional<Exception>>> result = journalOps(GENEROUS_TIMEOUT)
                .writeMessages(List.of(write("thing:a", 1, SERIALIZABLE), write("thing:a", 2, SERIALIZABLE)))
                .toCompletableFuture();

        assertThat(calls).containsExactly("thing:a:1");
        assertThat(result).isNotDone();

        first.complete(null);
        assertThat(calls).containsExactly("thing:a:1", "thing:a:2");
        assertThat(result).as("the batch waits for the second write").isNotDone();

        second.complete(null);
        assertThat(slots(result)).containsExactly(Optional.empty(), Optional.empty());
    }

    @Test
    public void failedWriteStopsTheLaterWrites() {
        final CompletableFuture<Void> first = handCompleted("thing:a", 1);
        final R2dbcDataIntegrityViolationException notNull =
                new R2dbcDataIntegrityViolationException("null value", "23502", 0);

        final CompletionStage<Iterable<Optional<Exception>>> result = journalOps(GENEROUS_TIMEOUT)
                .writeMessages(List.of(write("thing:a", 1, SERIALIZABLE), write("thing:a", 2, SERIALIZABLE)));
        first.completeExceptionally(notNull);

        assertThatThrownBy(() -> result.toCompletableFuture().get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCause(notNull);
        assertThat(calls).containsExactly("thing:a:1");
    }

    @Test
    public void rejectedWriteDoesNotStopTheLaterWrites() throws Exception {
        final CompletionStage<Iterable<Optional<Exception>>> result = journalOps(GENEROUS_TIMEOUT)
                .writeMessages(List.of(write("thing:a", 1, new Object()), write("thing:a", 2, SERIALIZABLE)));

        final List<Optional<Exception>> slots = slots(result);
        assertThat(slots).hasSize(2);
        assertThat(slots.get(0)).containsInstanceOf(IllegalArgumentException.class);
        assertThat(slots.get(1)).isEmpty();
        assertThat(calls).containsExactly("thing:a:2");
    }

    @Test
    public void resultSlotsFollowTheInputOrder() throws Exception {
        final CompletableFuture<Void> first = handCompleted("thing:a", 1);
        final CompletableFuture<Void> third = handCompleted("thing:a", 3);

        final CompletableFuture<Iterable<Optional<Exception>>> result = journalOps(GENEROUS_TIMEOUT)
                .writeMessages(List.of(write("thing:a", 1, SERIALIZABLE), write("thing:a", 2, new Object()),
                        write("thing:a", 3, SERIALIZABLE)))
                .toCompletableFuture();
        first.complete(null);
        third.complete(null);

        final List<Optional<Exception>> slots = slots(result);
        assertThat(slots).hasSize(3);
        assertThat(slots.get(0)).isEmpty();
        assertThat(slots.get(1)).containsInstanceOf(IllegalArgumentException.class);
        assertThat(slots.get(2)).isEmpty();
        assertThat(calls).containsExactly("thing:a:1", "thing:a:3");
    }

    @Test
    public void laterWriteIsNotStartedAfterTheCallTimeout() throws Exception {
        final CompletableFuture<Void> first = handCompleted("thing:a", 1);

        final CompletionStage<Iterable<Optional<Exception>>> result = journalOps(Duration.ofMillis(100))
                .writeMessages(List.of(write("thing:a", 1, SERIALIZABLE), write("thing:a", 2, SERIALIZABLE)));
        Thread.sleep(200);
        first.complete(null);

        assertThatThrownBy(() -> result.toCompletableFuture().get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(TimeoutException.class)
                .hasMessageContaining("<thing:a>")
                .hasMessageContaining("sequence number 2");
        assertThat(calls).containsExactly("thing:a:1");
    }

    /**
     * Pekko sends one batch per persistent actor, so a batch with several persistence ids does not occur; this pins
     * the rule for one anyway: input order, not per-pid order.
     */
    @Test
    public void writesOfSeveralPersistenceIdsRunInInputOrder() throws Exception {
        final CompletableFuture<Void> a1 = handCompleted("thing:a", 1);
        final CompletableFuture<Void> b1 = handCompleted("thing:b", 1);
        final CompletableFuture<Void> a2 = handCompleted("thing:a", 2);

        final CompletableFuture<Iterable<Optional<Exception>>> result = journalOps(GENEROUS_TIMEOUT)
                .writeMessages(List.of(write("thing:a", 1, SERIALIZABLE), write("thing:b", 1, SERIALIZABLE),
                        write("thing:a", 2, SERIALIZABLE)))
                .toCompletableFuture();
        assertThat(calls).containsExactly("thing:a:1");

        a1.complete(null);
        assertThat(calls).containsExactly("thing:a:1", "thing:b:1");

        b1.complete(null);
        assertThat(calls).containsExactly("thing:a:1", "thing:b:1", "thing:a:2");

        a2.complete(null);
        assertThat(slots(result)).containsExactly(Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Test
    public void laterWriteStartsWhileTheCallTimeoutHasNotPassed() throws Exception {
        final CompletableFuture<Void> first = handCompleted("thing:a", 1);

        final CompletionStage<Iterable<Optional<Exception>>> result = journalOps(GENEROUS_TIMEOUT)
                .writeMessages(List.of(write("thing:a", 1, SERIALIZABLE), write("thing:a", 2, SERIALIZABLE)));
        first.complete(null);

        assertThat(slots(result)).containsExactly(Optional.empty(), Optional.empty());
        assertThat(calls).containsExactly("thing:a:1", "thing:a:2");
    }

    @Test
    public void zeroCallTimeoutMeansNoDeadlineForLaterWrites() throws Exception {
        final CompletableFuture<Void> first = handCompleted("thing:a", 1);

        final CompletionStage<Iterable<Optional<Exception>>> result = journalOps(Duration.ZERO)
                .writeMessages(List.of(write("thing:a", 1, SERIALIZABLE), write("thing:a", 2, SERIALIZABLE)));
        Thread.sleep(50);
        first.complete(null);

        assertThat(slots(result)).containsExactly(Optional.empty(), Optional.empty());
        assertThat(calls).containsExactly("thing:a:1", "thing:a:2");
    }

    @Test
    public void negativeCallTimeoutIsRefused() {
        assertThatThrownBy(() -> PostgresJournalOps.of(operations, JsonTextPayloadCodec.INSTANCE,
                Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("callTimeout");
        assertThatThrownBy(() -> PostgresJournalOps.of(operations, JsonTextPayloadCodec.INSTANCE, null))
                .isInstanceOf(NullPointerException.class);
    }

    private PostgresJournalOps journalOps(final Duration callTimeout) {
        return PostgresJournalOps.of(operations, JsonTextPayloadCodec.INSTANCE, callTimeout);
    }

    private CompletableFuture<Void> handCompleted(final String pid, final long sn) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        futures.put(pid + ":" + sn, future);
        return future;
    }

    private static AtomicWrite write(final String pid, final long sn, final Object payload) {
        return AtomicWrite.apply(PersistentRepr.apply(payload, sn, pid, "", false, ActorRef.noSender(), "writer"));
    }

    private static List<Optional<Exception>> slots(final CompletionStage<Iterable<Optional<Exception>>> result)
            throws Exception {
        final List<Optional<Exception>> slots = new ArrayList<>();
        result.toCompletableFuture().get(5, TimeUnit.SECONDS).forEach(slots::add);
        return slots;
    }

}
