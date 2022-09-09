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

import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.internal.utils.akka.PingCommand;
import org.eclipse.ditto.internal.utils.akka.PingCommandResponse;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistentactors.config.PingConfig;
import org.eclipse.ditto.internal.utils.persistentactors.config.RateConfig;
import org.eclipse.ditto.json.JsonValue;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.Materializer;
import akka.stream.javadsl.Source;

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
            final MongoReadJournal readJournal) {

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
     * Creates Akka configuration object Props for this Actor.
     *
     * @param persistenceActorShardRegion the shard region of the target PersistenceActor.
     * @param pingConfig the Configuration to apply for this ping actor.
     * @param readJournal readJournal to extract current PIDs from.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef persistenceActorShardRegion, final PingConfig pingConfig,
            final MongoReadJournal readJournal) {

        return Props.create(PersistencePingActor.class, persistenceActorShardRegion, pingConfig, readJournal);
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param persistenceActorShardRegion the shard region of the target PersistenceActor.
     * @param pingConfig the Configuration to apply for this ping actor.
     * @param persistenceIdsSourceSupplier supplier of persistence id sources.
     * @return the Akka configuration Props object.
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
                .matchEquals(InternalMessages.PINGING_FINISHED, msg -> pingingFinished())
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
            pingInProgress = true;
            final Source<String, NotUsed> currentPersistenceIdsSource = persistenceIdsSourceSupplier.get();
            if (currentPersistenceIdsSource != null) {
                final RateConfig rateConfig = pingConfig.getRateConfig();
                currentPersistenceIdsSource
                        .throttle(rateConfig.getEntityAmount(), rateConfig.getFrequency())
                        .runForeach(this::ping, materializer)
                        .thenRun(() -> {
                            log.info("Sending pings completed.");
                            getSelf().tell(InternalMessages.PINGING_FINISHED, getSelf());
                        });
            } else {
                log.warning("Failed to create new persistence id source for persistence actor ping.");
            }
        }
    }

    private void pingingFinished() {
        log.info("Got ping completed.");
        pingInProgress = false;
    }

    private void ping(final String persistenceId) {
        final EntityId entityId = extractEntityIdFromPersistenceId(persistenceId);
        final String correlationId = toCorrelationId(entityId);
        final PingCommand pingMessage = PingCommand.of(entityId,
                correlationId,
                JsonValue.of(pingConfig.getJournalTag()));
        log.debug("Sending a 'ping' message for persistenceId <{}>: <{}>", persistenceId, pingMessage);
        persistenceActorShardRegion.tell(pingMessage, getSelf());
    }

    private EntityId extractEntityIdFromPersistenceId(final String persistenceId) {
        final int indexOfSeparator = persistenceId.indexOf(':');
        if (indexOfSeparator < 0) {
            final String message =
                    String.format("Persistence ID <%s> wasn't prefixed with an entity type.", persistenceId);
            log.error(message);
            throw new IllegalArgumentException(message);
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
        START_PINGING,
        PINGING_FINISHED
    }

}
