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

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.pattern.AskTimeoutException;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.persistence.AbstractPersistentActor;
import org.apache.pekko.persistence.AtomicWrite;
import org.apache.pekko.persistence.PersistentRepr;
import org.apache.pekko.persistence.journal.japi.AsyncWriteJournal;
import org.apache.pekko.testkit.javadsl.TestKit;

import reactor.pool.PoolAcquirePendingLimitException;
import scala.concurrent.Future;
import scala.jdk.javaapi.FutureConverters;

/**
 * Characterisation of Pekko persistence behaviour that the pool-overload design relies on. It runs <strong>no Ditto
 * persistence code</strong> — a stub journal plugin plus a minimal persistent actor — and pins two framework facts:
 * <ul>
 *     <li>a failed write future (what {@link PostgresJournalOps} returns for an acquire timeout or a pool overload)
 *     stops the persistent actor and answers the in-flight command with nothing, so the caller only learns about it by
 *     timing out;</li>
 *     <li>the residual risk the acquire budget bounds but cannot remove: a write still in flight when the caller's
 *     deadline expires commits anyway (scaled: an 800 ms write against a 400 ms deadline).</li>
 * </ul>
 * Kept apart from {@link PostgresJournalPoolOverloadTest} (the real journal chain) on purpose: it has its own actor
 * system and static stub state. Originally part of the 2026-08-21 pool-starvation investigation's
 * {@code PoolStarvationConsequenceProbeTest}.
 */
public final class PersistentActorWriteOutcomeCharacterisationTest {

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void startActorSystem() {
        final Config config = ConfigFactory.parseString(
                        "pekko.persistence.journal.plugin = \"stub-journal\"\n"
                                + "stub-journal.class = \"" + StubJournal.class.getName() + "\"\n"
                                + "pekko.loglevel = WARNING\n"
                                + "pekko.actor.provider = local\n")
                .withFallback(ConfigFactory.load());
        actorSystem = ActorSystem.create("persistent-actor-write-outcome-characterisation", config);
    }

    @AfterClass
    public static void stopActorSystem() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void failedWriteFutureStopsThePersistentActorAndAnswersTheCommandWithNothing() {
        new TestKit(actorSystem) {{
            StubJournal.MODE.set(StubJournal.Mode.FAIL_WITH_POOL_OVERLOAD);
            final ActorRef persistentActor =
                    actorSystem.actorOf(RecordingPersistentActor.props("stub:fail:1", getRef()));
            watch(persistentActor);

            persistentActor.tell("persist-me", getRef());

            expectTerminated(Duration.ofSeconds(10), persistentActor);
            expectNoMessage(Duration.ofSeconds(1));
        }};
    }

    @Test
    public void aWriteStillInFlightAtTheCallerDeadlineCommitsAnyway() {
        new TestKit(actorSystem) {{
            StubJournal.MODE.set(StubJournal.Mode.SLOW_SUCCESS);
            StubJournal.WRITE_DELAY.set(Duration.ofMillis(800));
            StubJournal.PERSISTED.set(0);
            final Duration callerDeadline = Duration.ofMillis(400);

            final ActorRef persistentActor =
                    actorSystem.actorOf(RecordingPersistentActor.props("stub:slow:1", getRef()));

            final CompletionStage<Object> ask = Patterns.ask(persistentActor, "persist-me", callerDeadline);

            assertThatThrownBy(() -> ask.toCompletableFuture().get(10, TimeUnit.SECONDS))
                    .as("the caller's deadline expires first, so the COMMAND fails")
                    .hasCauseInstanceOf(AskTimeoutException.class);
            expectMsg(Duration.ofSeconds(10), "persisted");
            assertThat(StubJournal.PERSISTED.get())
                    .as("the event IS written although the caller was told the operation failed")
                    .isEqualTo(1);
            watch(persistentActor);
            expectNoMessage(Duration.ofMillis(300));
        }};
    }

    /** Persists one event per command; reports to a watcher and acks only when the write succeeded. */
    public static final class RecordingPersistentActor extends AbstractPersistentActor {

        private final String persistenceId;
        private final ActorRef watcher;

        @SuppressWarnings("unused")
        public RecordingPersistentActor(final String persistenceId, final ActorRef watcher) {
            this.persistenceId = persistenceId;
            this.watcher = watcher;
        }

        static Props props(final String persistenceId, final ActorRef watcher) {
            return Props.create(RecordingPersistentActor.class, persistenceId, watcher);
        }

        @Override
        public String persistenceId() {
            return persistenceId;
        }

        @Override
        public AbstractActor.Receive createReceive() {
            return receiveBuilder()
                    .matchEquals("persist-me", command -> persist("{\"k\":1}", event -> {
                        watcher.tell("persisted", getSelf());
                        getSender().tell("ack", getSelf());
                    }))
                    .build();
        }

        @Override
        public AbstractActor.Receive createReceiveRecover() {
            return receiveBuilder().matchAny(any -> { }).build();
        }
    }

    /** A journal plugin whose write behaviour is switched per test. */
    public static final class StubJournal extends AsyncWriteJournal {

        enum Mode {FAIL_WITH_POOL_OVERLOAD, SLOW_SUCCESS}

        static final AtomicReference<Mode> MODE = new AtomicReference<>(Mode.SLOW_SUCCESS);
        static final AtomicReference<Duration> WRITE_DELAY = new AtomicReference<>(Duration.ZERO);
        static final AtomicInteger PERSISTED = new AtomicInteger();

        @Override
        public Future<Iterable<Optional<Exception>>> doAsyncWriteMessages(final Iterable<AtomicWrite> messages) {
            final int count = (int) StreamSupport.stream(messages.spliterator(), false).count();
            if (MODE.get() == Mode.FAIL_WITH_POOL_OVERLOAD) {
                // Exactly what PostgresJournalOps produces for a write beyond the pending bound.
                return FutureConverters.asScala(CompletableFuture.failedFuture(
                        PostgresPoolOverloadException.of(new PoolAcquirePendingLimitException(200))));
            }
            final CompletableFuture<Iterable<Optional<Exception>>> result = new CompletableFuture<>();
            CompletableFuture.delayedExecutor(WRITE_DELAY.get().toMillis(), TimeUnit.MILLISECONDS).execute(() -> {
                PERSISTED.addAndGet(count);
                result.complete(Collections.nCopies(count, Optional.<Exception>empty()));
            });
            return FutureConverters.asScala(result);
        }

        @Override
        public Future<Void> doAsyncDeleteMessagesTo(final String persistenceId, final long toSequenceNr) {
            return FutureConverters.asScala(CompletableFuture.completedFuture(null));
        }

        @Override
        public Future<Long> doAsyncReadHighestSequenceNr(final String persistenceId, final long fromSequenceNr) {
            return FutureConverters.asScala(CompletableFuture.completedFuture(0L));
        }

        @Override
        public Future<Void> doAsyncReplayMessages(final String persistenceId, final long fromSequenceNr,
                final long toSequenceNr, final long max, final Consumer<PersistentRepr> replayCallback) {
            return FutureConverters.asScala(CompletableFuture.completedFuture(null));
        }
    }

}
