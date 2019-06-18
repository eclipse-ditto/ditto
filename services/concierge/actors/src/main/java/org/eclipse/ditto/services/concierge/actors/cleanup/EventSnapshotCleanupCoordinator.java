/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.concierge.actors.cleanup;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.concierge.actors.ShardRegions;
import org.eclipse.ditto.services.concierge.actors.cleanup.credits.CreditDecisionSource;
import org.eclipse.ditto.services.concierge.actors.cleanup.messages.CreditDecision;
import org.eclipse.ditto.services.concierge.actors.cleanup.persistenceids.PersistenceIdSource;
import org.eclipse.ditto.services.concierge.common.PersistenceCleanupConfig;
import org.eclipse.ditto.services.models.connectivity.ConnectionTag;
import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.services.models.streaming.EntityIdWithRevision;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.akka.controlflow.Transistor;
import org.eclipse.ditto.signals.commands.cleanup.Cleanup;
import org.eclipse.ditto.signals.commands.cleanup.CleanupResponse;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import akka.Done;
import akka.NotUsed;
import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Pair;
import akka.japi.pf.PFBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.FanInShape2;
import akka.stream.Graph;
import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
import akka.stream.SourceShape;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import scala.PartialFunction;

/**
 * Cluster singleton actor to assembles the stream to cleanup old snapshots and events and report on its status.
 *
 * <pre>{@code
 *
 *    Report failures with timestamp
 *                 ^
 *                 |
 *                 |
 *                 |
 *             +---+---------------+
 *             |All persistence IDs+-------------------------------+
 *             +-------------------+                               |
 *                                                                 v            +------------------+
 *                                                              +--+--+         |Forward to        |
 *                                                              |Merge+-------->+PersistenceActor  |
 *                                                              +--+--+         |with back pressure|
 *                                                                 ^            +--------+---------+
 *                                                                 |                     |
 *                                                                 |                     |
 *             +-------+         +---------------+                 |                     |
 *             |Metrics+-------->+Credit decision+-----------------+                     |
 *             +-------+         +------+--------+                                       |
 *                                      |                                                |
 *                                      |                                                |
 *                                      v                                                v
 *                      Report metrics and credit decision                     Report round trip time
 *
 *
 * }</pre>
 */
public final class EventSnapshotCleanupCoordinator extends AbstractActorWithTimers {

    private static final String RESOURCE_TYPE = "resource-type";
    private static final String ERROR = "error";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final PersistenceCleanupConfig config;
    private final ActorRef pubSubMediator;
    private final ShardRegions shardRegions;
    private final ActorMaterializer materializer;

    // logs for status reporting
    private final Deque<Pair<Instant, CreditDecision>> creditDecisions;
    private final Deque<Pair<Instant, CleanupResponse>> actions;
    private final Deque<Pair<Instant, Event>> events;

    private EventSnapshotCleanupCoordinator(final PersistenceCleanupConfig config, final ActorRef pubSubMediator,
            final ShardRegions shardRegions) {

        this.config = config;
        this.pubSubMediator = pubSubMediator;
        this.shardRegions = shardRegions;

        materializer = ActorMaterializer.create(getContext());
        scheduleWakeUp();

        creditDecisions = new ArrayDeque<>(config.getKeptCreditDecisions() + 1);
        actions = new ArrayDeque<>(config.getKeptActions() + 1);
        events = new ArrayDeque<>(config.getKeptEvents() + 1);
    }

    private KillSwitch killSwitch;

    @Override
    public Receive createReceive() {
        return sleeping();
    }

    private Receive sleeping() {
        return ReceiveBuilder.create()
                .matchEquals(Event.WOKE_UP, this::wokeUp)
                .matchAny(message -> log.warning("Unexpected message while sleeping: <{}>", message))
                .build();
    }

    private Receive streaming() {
        return ReceiveBuilder.create()
                .match(CreditDecision.class, creditDecision ->
                        enqueue(creditDecisions, creditDecision, config.getKeptCreditDecisions()))
                .match(CleanupResponse.class, cleanupResponse ->
                        enqueue(actions, cleanupResponse, config.getKeptActions()))
                .matchEquals(Event.STREAM_TERMINATED, this::streamTerminated)
                .matchAny(message -> log.warning("Unexpected message while streaming: <{}>", message))
                .build();
    }

    private void wokeUp(final Event wokeUp) {
        log.info("Woke up.");
        enqueue(events, wokeUp, config.getKeptEvents());
        restartStream();
        getContext().become(streaming());
    }

    private void streamTerminated(final Event streamTerminated) {
        log.warning("Stream terminated. will restart after quiet period.");
        enqueue(events, streamTerminated, config.getKeptEvents());
        scheduleWakeUp();
        getContext().become(sleeping());
    }

    private <T> Flow<T, T, NotUsed> reportToSelf() {
        return Flow.fromFunction(x -> {
            getSelf().tell(x, getSelf());
            return x;
        });
    }

    private void restartStream() {
        if (killSwitch != null) {
            log.info("Shutting down previous stream.");
            killSwitch.shutdown();
            killSwitch = null;
        }

        Pair<UniqueKillSwitch, CompletionStage<Done>> materializedValues =
                assembleSource().viaMat(KillSwitches.single(), Keep.right())
                        .toMat(forwarderSink(), Keep.both())
                        .run(materializer);

        killSwitch = materializedValues.first();

        materializedValues.second()
                .<Void>handle((result, error) -> {
                    log.error("Stream terminated. Result=<{}> Error=<{}>", result, error);
                    getSelf().tell(Event.STREAM_TERMINATED, getSelf());
                    return null;
                });
    }

    private void scheduleWakeUp() {
        getTimers().startSingleTimer(Event.WOKE_UP, Event.WOKE_UP, config.getQuietPeriod());
    }

    private Source<EntityIdWithRevision, NotUsed> assembleSource() {
        return Source.fromGraph(GraphDSL.create(builder -> {
            final SourceShape<EntityIdWithRevision> persistenceIds = builder.add(persistenceIdSource());
            final SourceShape<Integer> credit = builder.add(creditSource());
            final FanInShape2<EntityIdWithRevision, Integer, EntityIdWithRevision> transistor =
                    builder.add(Transistor.of());

            builder.from(persistenceIds.out()).toInlet(transistor.in0());
            builder.from(credit.out()).toInlet(transistor.in1());

            return SourceShape.of(transistor.out());
        }));
    }

    private Source<Integer, NotUsed> creditSource() {
        final Graph<SourceShape<CreditDecision>, NotUsed> creditDecisionSource =
                CreditDecisionSource.create(config.getCreditDecisionConfig(), getContext(), pubSubMediator, log);

        return Source.fromGraph(creditDecisionSource).via(reportToSelf()).map(CreditDecision::getCredit);
    }

    private Graph<SourceShape<EntityIdWithRevision>, NotUsed> persistenceIdSource() {
        return PersistenceIdSource.createInfiniteSource(config.getPersistenceIdsConfig(), pubSubMediator);
    }

    // include self-reporting for acknowledged
    private Sink<EntityIdWithRevision, CompletionStage<Done>> forwarderSink() {

        final PartialFunction<EntityIdWithRevision, CompletionStage<CleanupResponse>> askShardRegionByTagType =
                new PFBuilder<EntityIdWithRevision, CompletionStage<CleanupResponse>>()
                        .match(ThingTag.class, thingTag ->
                                askShardRegion(shardRegions.things(), ThingCommand.RESOURCE_TYPE, thingTag))
                        .match(PolicyTag.class, policyTag ->
                                askShardRegion(shardRegions.policies(), PolicyCommand.RESOURCE_TYPE, policyTag))
                        .match(ConnectionTag.class, connTag ->
                                askShardRegion(shardRegions.connections(), ConnectivityCommand.RESOURCE_TYPE, connTag))
                        .matchAny(e -> {
                            final DittoHeaders headers = DittoHeaders.newBuilder()
                                    .putHeader(ERROR, "Unexpected entity ID type: " + e)
                                    .build();
                            return CompletableFuture.completedFuture(CleanupResponse.failure(e.getId(), headers));
                        })
                        .build();

        return Flow.<EntityIdWithRevision>create()
                .mapAsync(config.getParallelism(), askShardRegionByTagType::apply)
                .via(reportToSelf())
                .toMat(Sink.ignore(), Keep.right());
    }

    private CompletionStage<CleanupResponse> askShardRegion(final ActorRef shardRegion, final String resourceType,
            final EntityIdWithRevision tag) {
        final String id = tag.getId();
        final Cleanup command = getCommand(resourceType, id);
        return Patterns.ask(shardRegion, command, config.getCleanupTimeout())
                .handle((result, error) -> {
                    if (result instanceof CleanupResponse) {
                        return (CleanupResponse) result;
                    } else {
                        final DittoHeaders headers = command.getDittoHeaders()
                                .toBuilder()
                                .putHeader(ERROR, "Unexpected response from shard: " + result + "; Error: " + error)
                                .build();
                        return CleanupResponse.failure(id, headers);
                    }
                });
    }

    private static Cleanup getCommand(final String resourceType, final String id) {
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(RESOURCE_TYPE, resourceType)
                .build();
        return Cleanup.of(id, headers);
    }

    private static <T> void enqueue(final Deque<Pair<Instant, T>> queue, final T element, final int maxQueueSize) {
        queue.addFirst(Pair.create(Instant.now(), element));
        if (queue.size() > maxQueueSize) {
            queue.removeLast();
        }
    }

    private enum Event {
        WOKE_UP,
        STREAM_TERMINATED
    }
}
