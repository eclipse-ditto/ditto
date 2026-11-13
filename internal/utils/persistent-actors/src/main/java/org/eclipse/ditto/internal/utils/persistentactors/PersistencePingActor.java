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
package org.eclipse.ditto.internal.utils.persistentactors;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.internal.utils.pekko.PingCommand;
import org.eclipse.ditto.internal.utils.pekko.PingCommandResponse;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.persistence.api.DittoReadJournal;
import org.eclipse.ditto.internal.utils.persistentactors.config.PingConfig;
import org.eclipse.ditto.internal.utils.persistentactors.config.RateConfig;
import org.eclipse.ditto.json.JsonValue;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Cancellable;
import org.apache.pekko.actor.Props;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Source;

/**
 * Actor which pings an {@link AbstractPersistenceActor}s containing journal entries tagged with
 * a configured {@link org.eclipse.ditto.internal.utils.persistentactors.config.PingConfig#getJournalTag()} automatically on a (cold) startup of the cluster.
 * <p>
 * Also periodically sends out ping messages to e.g. mitigate crashes of {@code AbstractPersistenceActor}s
 * which should be always kept alive.
 */
public final class PersistencePingActor extends AbstractActor {

    /**
     * The name of this Actor.
     */
    public static final String ACTOR_NAME = "persistencePing";

    private static final String CORRELATION_ID_PREFIX = "persistence-ping-actor-triggered:";

    private final ThreadSafeDittoLoggingAdapter log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);

    private final ActorRef persistenceActorShardRegion;
    private final Supplier<Source<String, NotUsed>> persistenceIdsSourceSupplier;
    private final PingConfig pingConfig;
    private final Materializer materializer;

    @Nullable private Cancellable pingCheck = null;
    private boolean pingInProgress = false;
    /**
     * In ID order: the last pid a failed walk reached; the next walk continues after it. Kept in memory only: after a
     * restart of the actor the next walk starts from the beginning.
     */
    @Nullable private String resumeAfter = null;

    @SuppressWarnings("unused")
    private PersistencePingActor(final ActorRef persistenceActorShardRegion, final PingConfig pingConfig,
            final Supplier<Source<String, NotUsed>> persistenceIdsSourceSupplier) {

        this.persistenceActorShardRegion = persistenceActorShardRegion;
        this.persistenceIdsSourceSupplier = persistenceIdsSourceSupplier;
        this.pingConfig = pingConfig;
        materializer = Materializer.createMaterializer(this::getContext);
    }

    @SuppressWarnings("unused")
    private PersistencePingActor(final ActorRef persistenceActorShardRegion, final PingConfig pingConfig,
            final DittoReadJournal readJournal) {

        this.persistenceActorShardRegion = persistenceActorShardRegion;
        this.pingConfig = pingConfig;
        materializer = Materializer.createMaterializer(this::getContext);
        final PingConfig.StreamingOrder streamingOrder = pingConfig.getStreamingOrder();
        switch (streamingOrder) {
            case TAGS:
                persistenceIdsSourceSupplier = () ->
                        readJournal.getJournalPidsWithTagOrderedByPriorityTag(pingConfig.getJournalTag(),
                                pingConfig.getInterval());
                break;
            case ID:
            default:
                persistenceIdsSourceSupplier = () -> readJournal.getJournalPidsWithTag(pingConfig.getJournalTag(),
                        pingConfig.getReadJournalBatchSize(),
                        pingConfig.getInterval(),
                        materializer,
                        false);
        }
        readJournal.ensureTagPidIndex().exceptionally(e -> {
            log.error(e, "Failed to create TagPidIndex");
            return null;
        });
    }

    /**
     * Creates Pekko configuration object Props for this Actor.
     *
     * @param persistenceActorShardRegion the shard region of the target PersistenceActor.
     * @param pingConfig the Configuration to apply for this ping actor.
     * @param readJournal readJournal to extract current PIDs from.
     * @return the Pekko configuration Props object.
     */
    public static Props props(final ActorRef persistenceActorShardRegion, final PingConfig pingConfig,
            final DittoReadJournal readJournal) {

        return Props.create(PersistencePingActor.class, persistenceActorShardRegion, pingConfig, readJournal);
    }

    /**
     * Creates Pekko configuration object Props for this Actor.
     *
     * @param persistenceActorShardRegion the shard region of the target PersistenceActor.
     * @param pingConfig the Configuration to apply for this ping actor.
     * @param persistenceIdsSourceSupplier supplier of persistence id sources.
     * @return the Pekko configuration Props object.
     */
    static Props propsForTests(final ActorRef persistenceActorShardRegion, final PingConfig pingConfig,
            final Supplier<Source<String, NotUsed>> persistenceIdsSourceSupplier) {

        return Props.create(PersistencePingActor.class, persistenceActorShardRegion, pingConfig,
                persistenceIdsSourceSupplier);
    }

    private Cancellable schedulePing() {
        final ActorContext context = getContext();
        final InternalMessages message = InternalMessages.START_PINGING;

        log.info("Scheduling ping for all PersistenceActors with initial delay <{}> and interval <{}>.",
                pingConfig.getInitialDelay(), pingConfig.getInterval());

        return context.getSystem().scheduler()
                .scheduleAtFixedRate(pingConfig.getInitialDelay(), pingConfig.getInterval(), getSelf(),
                        message, context.dispatcher(), ActorRef.noSender());
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        pingCheck = schedulePing();
    }

    @Override
    public void postStop() throws Exception {
        if (null != pingCheck) {
            pingCheck.cancel();
        }
        super.postStop();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(PingCommandResponse.class,
                        command -> log.debug("Received PersistencePingCommandResponse with correlation-id <{}> " +
                                        "and payload <{}> from sender <{}>",
                                command.getCorrelationId(), command.getPayload(), getSender()))
                .match(DittoRuntimeException.class,
                        exception -> log.debug("Received <{}> for correlation-id <{}>: {}",
                                exception.getClass().getSimpleName(),
                                exception.getDittoHeaders().getCorrelationId().orElse("unknown"),
                                exception.getMessage()))
                .matchEquals(InternalMessages.START_PINGING, msg -> startPinging())
                .match(PingIterationFinished.class, this::pingIterationFinished)
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void startPinging() {
        if (pingInProgress) {
            log.info("Another ping iteration is currently in progress. Next iteration will be started after <{}>.",
                    pingConfig.getInterval());
        } else {
            log.info("Sending ping for PersistenceActors. Will be sent again after the configured interval of <{}>.",
                    pingConfig.getInterval());
            final Source<String, NotUsed> currentPersistenceIdsSource = persistenceIdsSourceSupplier.get();
            if (currentPersistenceIdsSource != null) {
                pingInProgress = true;
                final RateConfig rateConfig = pingConfig.getRateConfig();
                final ActorRef self = getSelf();
                final AtomicLong pinged = new AtomicLong();
                final AtomicLong skipped = new AtomicLong();
                final AtomicReference<String> lastReached = new AtomicReference<>();
                final long startNanos = System.nanoTime();
                @Nullable final String continueAfter = resumeAfter;
                final Source<String, NotUsed> walk;
                if (continueAfter != null) {
                    log.info("Continuing after <{}>, the last PersistenceActor the previous, failed iteration reached.",
                            continueAfter);
                    // drop before the throttle: the skipped pids cost no ping slot.
                    walk = currentPersistenceIdsSource.dropWhile(pid -> isInPidOrderNotAfter(pid, continueAfter));
                } else {
                    walk = currentPersistenceIdsSource;
                }
                walk.throttle(rateConfig.getEntityAmount(), rateConfig.getFrequency())
                        .runForeach(persistenceId -> {
                            if (ping(persistenceId)) {
                                pinged.incrementAndGet();
                            } else {
                                skipped.incrementAndGet();
                            }
                            lastReached.set(persistenceId);
                        }, materializer)
                        // every completion path, failure included, ends the iteration: otherwise a failed stream
                        // would leave pingInProgress set and no later iteration would ever start.
                        .whenComplete((done, failure) -> self.tell(new PingIterationFinished(pinged.get(),
                                skipped.get(), lastReached.get(), continueAfter,
                                Duration.ofNanos(System.nanoTime() - startNanos), failure), self));
            } else {
                log.warning("Failed to create new persistence id source for persistence actor ping.");
            }
        }
    }

    private void pingIterationFinished(final PingIterationFinished finished) {
        pingInProgress = false;
        if (finished.failure() == null) {
            resumeAfter = null;
            log.info("Ping iteration completed: pinged <{}> PersistenceActors in <{}>{}.", finished.pinged(),
                    finished.duration(), finished.skipped() == 0
                            ? ""
                            : ", skipped <" + finished.skipped() + "> persistence ids that are no entity ids");
        } else {
            if (pingConfig.getStreamingOrder() == PingConfig.StreamingOrder.ID) {
                if (finished.lastReached() != null) {
                    // a walk in ID order delivers pids in ascending order, so the next walk can continue after the
                    // last one reached; without, a failure that recurs within a long walk would starve its tail.
                    resumeAfter = finished.lastReached();
                } else if (finished.resumedAfter() != null) {
                    // a resumed walk that failed before reaching any pid would fail there again on every resume and
                    // ping nothing at all: give that position up, so that the next walk pings from the beginning.
                    resumeAfter = null;
                    log.warning("The ping iteration that continued after <{}> failed before reaching any further "
                            + "PersistenceActor; giving that position up: the next iteration starts from the "
                            + "beginning.", finished.resumedAfter());
                }
            }
            final String next = "every <" + pingConfig.getInterval() + ">"
                    + (resumeAfter == null ? "" : ", and continues after <" + resumeAfter + ">");
            log.warning("Ping iteration failed after pinging <{}> PersistenceActors in <{}>; the next iteration "
                            + "starts as scheduled, {}. Cause: {}", finished.pinged(), finished.duration(), next,
                    describe(finished.failure()));
        }
    }

    /**
     * Whether {@code pid} comes at or before {@code bound} in the order of an ID-order walk: unsigned byte order of
     * UTF-8, the order of the pid column ({@code COLLATE "C"}) on PostgreSQL and of strings on MongoDB.
     */
    private static boolean isInPidOrderNotAfter(final String pid, final String bound) {
        return Arrays.compareUnsigned(pid.getBytes(StandardCharsets.UTF_8), bound.getBytes(StandardCharsets.UTF_8))
                <= 0;
    }

    private static String describe(final Throwable failure) {
        final Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        return cause.toString();
    }

    /**
     * Pings the persistence actor of a persistence id, or skips the id if it cannot be turned into an entity id (no
     * entity-type prefix, or an id the entity type rejects). The ping is a best-effort wake-up of many entities: one
     * malformed id in the journal must not stop it, which it would on every walk, at the same position, on any
     * backend, if it failed the stream.
     *
     * @return whether the persistence actor was pinged.
     */
    private boolean ping(final String persistenceId) {
        final EntityId entityId;
        try {
            entityId = extractEntityIdFromPersistenceId(persistenceId);
        } catch (final RuntimeException e) {
            log.warning("Skipping persistence id <{}>: it is no entity id a ping can address ({}).", persistenceId,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return false;
        }
        final String correlationId = toCorrelationId(entityId);
        final PingCommand pingMessage = PingCommand.of(entityId,
                correlationId,
                JsonValue.of(pingConfig.getJournalTag()));
        log.debug("Sending a 'ping' message for persistenceId <{}>: <{}>", persistenceId, pingMessage);
        persistenceActorShardRegion.tell(pingMessage, getSelf());
        return true;
    }

    private EntityId extractEntityIdFromPersistenceId(final String persistenceId) {
        final int indexOfSeparator = persistenceId.indexOf(':');
        if (indexOfSeparator < 0) {
            throw new IllegalArgumentException(
                    String.format("Persistence ID <%s> wasn't prefixed with an entity type.", persistenceId));
        }
        final String id = persistenceId.substring(indexOfSeparator + 1);
        final EntityType type = EntityType.of(persistenceId.substring(0, indexOfSeparator));
        return EntityId.of(type, id);
    }

    static String toCorrelationId(final EntityId persistenceId) {
        return CORRELATION_ID_PREFIX + persistenceId;
    }

    static Optional<String> toPersistenceId(final String correlationId) {
        if (correlationId.startsWith(CORRELATION_ID_PREFIX)) {
            return Optional.of(correlationId.replace(CORRELATION_ID_PREFIX, ""));
        }
        return Optional.empty();
    }

    enum InternalMessages {
        START_PINGING
    }

    /**
     * Sent to self when a ping iteration's stream completes, successfully or not.
     *
     * @param pinged how many persistence actors the iteration pinged.
     * @param skipped how many persistence ids the iteration skipped because they are no entity ids.
     * @param lastReached the persistence id reached last (pinged or skipped), or {@code null} if none was reached.
     * @param resumedAfter the position the iteration continued after, or {@code null} if it started at the beginning.
     * @param duration how long the iteration ran.
     * @param failure the stream's failure, or {@code null} on success.
     */
    private record PingIterationFinished(long pinged, long skipped, @Nullable String lastReached,
            @Nullable String resumedAfter, Duration duration, @Nullable Throwable failure) {}

}
