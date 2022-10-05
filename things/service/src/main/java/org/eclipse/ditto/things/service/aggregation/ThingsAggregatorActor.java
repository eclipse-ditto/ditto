/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.aggregation;

import static org.eclipse.ditto.things.api.ThingsMessagingConstants.THINGS_AGGREGATOR_ACTOR_NAME;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.akka.actors.AbstractActorWithShutdownBehavior;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.CoordinatedShutdown;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.stream.SourceRef;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamRefs;
import akka.util.Timeout;

/**
 * Actor to aggregate the retrieved Things from persistence.
 */
public final class ThingsAggregatorActor extends AbstractActorWithShutdownBehavior {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = THINGS_AGGREGATOR_ACTOR_NAME;

    private final ThreadSafeDittoLoggingAdapter log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);
    private final ActorRef targetActor;
    private final java.time.Duration retrieveSingleThingTimeout;
    private final int maxParallelism;
    private final ActorRef pubSubMediator;

    @SuppressWarnings("unused")
    private ThingsAggregatorActor(final ActorRef targetActor, final ThingsAggregatorConfig aggregatorConfig,
            final ActorRef pubSubMediator) {
        this.targetActor = targetActor;
        this.pubSubMediator = pubSubMediator;
        retrieveSingleThingTimeout = aggregatorConfig.getSingleRetrieveThingTimeout();
        maxParallelism = aggregatorConfig.getMaxParallelism();
    }

    /**
     * Creates Akka configuration object Props for this ThingsAggregatorActor.
     *
     * @param targetActor the Actor selection to delegate "asks" for the aggregation to.
     * @return the Akka configuration Props object
     */
    public static Props props(final ActorRef targetActor, final ThingsAggregatorConfig aggregatorConfig,
            final ActorRef pubSubMediator) {
        return Props.create(ThingsAggregatorActor.class, targetActor, aggregatorConfig, pubSubMediator);
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        final var self = getSelf();
        final var mediator = DistributedPubSub.get(getContext().getSystem()).mediator();
        // register on pub/sub so that others may send "RetrieveThings" messages to the aggregator:
        mediator.tell(DistPubSubAccess.subscribeViaGroup(RetrieveThings.TYPE, ACTOR_NAME, self), self);
        mediator.tell(DistPubSubAccess.subscribeViaGroup(SudoRetrieveThings.TYPE, ACTOR_NAME, self), self);

        final var coordinatedShutdown = CoordinatedShutdown.get(getContext().getSystem());
        final var serviceUnbindTask = "service-unbind-" + ACTOR_NAME;
        coordinatedShutdown.addTask(CoordinatedShutdown.PhaseServiceUnbind(), serviceUnbindTask,
                () -> Patterns.ask(self, Control.SERVICE_UNBIND, SHUTDOWN_ASK_TIMEOUT)
                        .thenApply(reply -> Done.done())
        );
    }

    @Override
    public Receive handleMessage() {
        return ReceiveBuilder.create()
                // # handle "RetrieveThings" command
                .match(RetrieveThings.class, rt -> {
                    log.withCorrelationId(rt)
                            .info("Got '{}' message. Retrieving requested '{}' Things..",
                                    RetrieveThings.class.getSimpleName(),
                                    rt.getEntityIds().size());
                    retrieveThings(rt, getSender());
                })

                // # handle "SudoRetrieveThings" command
                .match(SudoRetrieveThings.class, rt -> {
                    log.withCorrelationId(rt)
                            .info("Got '{}' message. Retrieving requested '{}' Things..",
                                    SudoRetrieveThings.class.getSimpleName(),
                                    rt.getThingIds().size());
                    retrieveThings(rt, getSender());
                })

                // # handle unknown message
                .matchAny(m -> {
                    log.warning("Got unknown message: {}", m);
                    unhandled(m);
                })

                // # build PartialFunction
                .build();
    }

    @Override
    public void serviceUnbind(final Control serviceUnbind) {
        log.info("{}: unsubscribing from pubsub for {} actor", serviceUnbind, ACTOR_NAME);

        final CompletableFuture<Done> unsubscribeTask = CompletableFuture.allOf(
                        Patterns.ask(pubSubMediator,
                                        DistPubSubAccess.unsubscribeViaGroup(RetrieveThings.TYPE, ACTOR_NAME, getSelf()),
                                        SHUTDOWN_ASK_TIMEOUT)
                                .toCompletableFuture(),
                        Patterns.ask(pubSubMediator,
                                        DistPubSubAccess.unsubscribeViaGroup(SudoRetrieveThings.TYPE, ACTOR_NAME, getSelf()),
                                        SHUTDOWN_ASK_TIMEOUT)
                                .toCompletableFuture())
                .thenApply(ack -> {
                    log.info("Unsubscribed successfully from pubsub for {} actor", ACTOR_NAME);
                    return Done.getInstance();
                });

        Patterns.pipe(unsubscribeTask, getContext().getDispatcher()).to(getSender());
    }

    @Override
    public void serviceRequestsDone(final Control serviceRequestsDone) {
        // nothing to do
    }

    private void retrieveThings(final RetrieveThings retrieveThings, final ActorRef resultReceiver) {
        final JsonFieldSelector selectedFields = retrieveThings.getSelectedFields().orElse(null);
        retrieveThingsAndSendResult(retrieveThings.getEntityIds(), selectedFields, retrieveThings, resultReceiver);
    }

    private void retrieveThings(final SudoRetrieveThings sudoRetrieveThings, final ActorRef resultReceiver) {
        final JsonFieldSelector selectedFields = sudoRetrieveThings.getSelectedFields().orElse(null);
        retrieveThingsAndSendResult(sudoRetrieveThings.getThingIds(), selectedFields, sudoRetrieveThings,
                resultReceiver);
    }

    private void retrieveThingsAndSendResult(final Collection<ThingId> thingIds,
            @Nullable final JsonFieldSelector selectedFields,
            final Command<?> command, final ActorRef resultReceiver) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final SourceRef<Jsonifiable> commandResponseSource = Source.from(thingIds)
                .filter(Objects::nonNull)
                .map(thingId -> {
                    final SignalWithEntityId<?> retrieveThing;
                    if (command instanceof RetrieveThings) {
                        retrieveThing = Optional.ofNullable(selectedFields)
                                .map(sf -> RetrieveThing.getBuilder(thingId, dittoHeaders)
                                        .withSelectedFields(sf)
                                        .build())
                                .orElse(RetrieveThing.of(thingId, dittoHeaders));
                    } else {
                        retrieveThing = Optional.ofNullable(selectedFields)
                                .map(sf -> SudoRetrieveThing.of(thingId, sf, dittoHeaders))
                                .orElse(SudoRetrieveThing.of(thingId, dittoHeaders));
                    }
                    log.withCorrelationId(dittoHeaders).info("Retrieving thing with ID <{}>", thingId);

                    return retrieveThing;
                })
                .ask(calculateParallelism(thingIds), targetActor, Jsonifiable.class,
                        Timeout.apply(retrieveSingleThingTimeout.toMillis(), TimeUnit.MILLISECONDS))
                .log("command-response", log)
                .runWith(StreamRefs.sourceRef(), SystemMaterializer.get(getContext().getSystem()).materializer());

        resultReceiver.tell(commandResponseSource, getSelf());
    }

    private int calculateParallelism(final Collection<ThingId> thingIds) {
        final int size = thingIds.size();
        if (size < maxParallelism / 2) {
            return size;
        } else if (size < maxParallelism) {
            return size / 2;
        } else {
            return maxParallelism;
        }
    }

}
