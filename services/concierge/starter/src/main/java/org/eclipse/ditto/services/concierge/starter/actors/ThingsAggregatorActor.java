/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.starter.actors;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.concierge.util.config.AbstractConciergeConfigReader;
import org.eclipse.ditto.services.models.concierge.ConciergeWrapper;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.PatternsCS;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamRefs;
import akka.util.Timeout;
import scala.concurrent.ExecutionContext;

/**
 * Actor to aggregate the retrieved Things from persistence.
 */
public final class ThingsAggregatorActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "aggregator";

    private static final String AGGREGATOR_INTERNAL_DISPATCHER = "aggregator-internal-dispatcher";

    private static final Pattern THING_ID_PATTERN = Pattern.compile(Thing.ID_REGEX);

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final ActorRef targetActor;
    private final ExecutionContext aggregatorDispatcher;
    private final java.time.Duration retrieveSingleThingTimeout;
    private final int maxParallelism;
    private final ActorMaterializer actorMaterializer;

    private ThingsAggregatorActor(final AbstractConciergeConfigReader configReader, final ActorRef targetActor) {
        this.targetActor = targetActor;
        aggregatorDispatcher = getContext().system().dispatchers().lookup(AGGREGATOR_INTERNAL_DISPATCHER);
        retrieveSingleThingTimeout = configReader.thingsAggregatorSingleRetrieveThingTimeout();
        maxParallelism = configReader.thingsAggregatorMaxParallelism();
        actorMaterializer = ActorMaterializer.create(getContext());
    }

    /**
     * Creates Akka configuration object Props for this ThingsAggregatorActor.
     *
     * @param configReader the configReader for the concierge service.
     * @param targetActor the Actor selection to delegate "asks" for the aggregation to.
     * @return the Akka configuration Props object
     */
    public static Props props(final AbstractConciergeConfigReader configReader, final ActorRef targetActor) {
        return Props.create(ThingsAggregatorActor.class, new Creator<ThingsAggregatorActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ThingsAggregatorActor create() {
                return new ThingsAggregatorActor(configReader, targetActor);
            }
        }).withDispatcher(AGGREGATOR_INTERNAL_DISPATCHER);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                // # handle "RetrieveThings" command
                .match(RetrieveThings.class, rt -> {
                    LogUtil.enhanceLogWithCorrelationId(log, rt.getDittoHeaders().getCorrelationId());
                    log.info("Got '{}' message. Retrieving requested '{}' Things..",
                            RetrieveThings.class.getSimpleName(),
                            rt.getThingIds().size());
                    retrieveThings(rt, getSender());
                })

                // # handle "SudoRetrieveThings" command
                .match(SudoRetrieveThings.class, rt -> {
                    LogUtil.enhanceLogWithCorrelationId(log, rt.getDittoHeaders().getCorrelationId());
                    log.info("Got '{}' message. Retrieving requested '{}' Things..",
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

    private void retrieveThings(final RetrieveThings retrieveThings, final ActorRef resultReceiver) {
        final JsonFieldSelector selectedFields = retrieveThings.getSelectedFields().orElse(null);
        retrieveThingsAndSendResult(retrieveThings.getThingIds(), selectedFields, retrieveThings, resultReceiver);
    }

    private void retrieveThings(final SudoRetrieveThings sudoRetrieveThings, final ActorRef resultReceiver) {
        final JsonFieldSelector selectedFields = sudoRetrieveThings.getSelectedFields().orElse(null);
        retrieveThingsAndSendResult(sudoRetrieveThings.getThingIds(), selectedFields, sudoRetrieveThings,
                resultReceiver);
    }

    private void retrieveThingsAndSendResult(final List<String> thingIds,
            @Nullable final JsonFieldSelector selectedFields,
            final Command<?> command, final ActorRef resultReceiver) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final CompletionStage<?> commandResponseSource = Source.from(thingIds)
                .filter(Objects::nonNull)
                .filterNot(String::isEmpty)
                .filter(thingId -> THING_ID_PATTERN.matcher(thingId).matches())
                .map(thingId -> {
                    final Command<?> toBeWrapped;
                    if (command instanceof RetrieveThings) {
                        toBeWrapped = Optional.ofNullable(selectedFields)
                                .map(sf -> RetrieveThing.getBuilder(thingId, dittoHeaders)
                                        .withSelectedFields(sf)
                                        .build())
                                .orElse(RetrieveThing.of(thingId, dittoHeaders));
                    } else {
                        toBeWrapped = Optional.ofNullable(selectedFields)
                                .map(sf -> SudoRetrieveThing.of(thingId, sf, dittoHeaders))
                                .orElse(SudoRetrieveThing.of(thingId, dittoHeaders));
                    }
                    return ConciergeWrapper.wrapForEnforcer(toBeWrapped);
                })
                .ask(calculateParallelism(thingIds), targetActor, Jsonifiable.class,
                        Timeout.apply(retrieveSingleThingTimeout.toMillis(), TimeUnit.MILLISECONDS))
                .log("command-response", log)
                .runWith(StreamRefs.sourceRef(), actorMaterializer);

        PatternsCS.pipe(commandResponseSource, aggregatorDispatcher)
                .to(resultReceiver);
    }

    private int calculateParallelism(final List<String> thingIds) {
        final int size = thingIds.size();
        if (size < (maxParallelism / 2)) {
            return size;
        } else if (size < maxParallelism) {
            return size / 2;
        } else {
            return maxParallelism;
        }
    }

}
