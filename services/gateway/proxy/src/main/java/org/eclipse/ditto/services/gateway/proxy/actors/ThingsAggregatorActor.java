/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.proxy.actors;

import static java.util.stream.Collectors.toList;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.gateway.starter.service.util.ConfigKeys;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoCommand;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingsResponse;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.WithEntity;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.dispatch.Futures;
import akka.dispatch.Mapper;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.util.Timeout;
import kamon.Kamon;
import kamon.trace.TraceContext;
import scala.Option;
import scala.Some;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

/**
 * Actor to aggregate the retrieved Things from persistence.
 */
public final class ThingsAggregatorActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "aggregator";

    private static final String TRACE_AGGREGATOR_RETRIEVE_THINGS = "aggregator.retrievethings";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final ActorRef targetActor;
    private final ExecutionContext aggregatorDispatcher;
    private final Matcher thingIdMatcher;
    private final java.time.Duration retrieveSingleThingTimeout;

    private ThingsAggregatorActor(final ActorRef targetActor) {
        this.targetActor = targetActor;
        aggregatorDispatcher = getContext().system().dispatchers().lookup("aggregator-internal-dispatcher");
        thingIdMatcher = Pattern.compile(Thing.ID_REGEX).matcher("");
        retrieveSingleThingTimeout = getContext().getSystem()
                .settings()
                .config()
                .getDuration(ConfigKeys.THINGS_AGGREGATOR_SINGLE_RETRIEVE_THING_TIMEOUT);
    }

    /**
     * Creates Akka configuration object Props for this ThingsAggregatorActor.
     *
     * @param targetActor the Actor selection to delegate "asks" for the aggregation to.
     * @return the Akka configuration Props object
     */
    public static Props props(final ActorRef targetActor) {
        return Props.create(ThingsAggregatorActor.class, new Creator<ThingsAggregatorActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ThingsAggregatorActor create() throws Exception {
                return new ThingsAggregatorActor(targetActor);
            }
        }).withDispatcher("aggregator-internal-dispatcher");
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

    private void retrieveThingsAndSendResult(final List<String> thingIds, final JsonFieldSelector selectedFields,
            final Command command, final ActorRef resultReceiver) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Option<String> token =
                dittoHeaders.getCorrelationId()
                        .map(cId -> (Option<String>) Some.apply(cId))
                        .orElse(Option.<String>empty());
        final TraceContext traceContext = Kamon.tracer().newContext(TRACE_AGGREGATOR_RETRIEVE_THINGS, token);

        final List<Future<Object>> futures = thingIds.stream() //
                .filter(thingId -> thingIdMatcher.reset(thingId).matches()) //
                .map(thingId -> {
                    final Command retrieve;
                    if (command instanceof RetrieveThings) {
                        retrieve = Optional.ofNullable(selectedFields)
                                .map(sf -> RetrieveThing.getBuilder(thingId, dittoHeaders)
                                        .withSelectedFields(sf)
                                        .build())
                                .orElse(RetrieveThing.of(thingId, dittoHeaders));
                    } else {
                        retrieve = Optional.ofNullable(selectedFields)
                                .map(sf -> SudoRetrieveThing.of(thingId, sf, dittoHeaders))
                                .orElse(SudoRetrieveThing.of(thingId, dittoHeaders));
                    }
                    return askTargetActor(retrieve);
                }) //
                .collect(toList());

        final Future<Iterable<Object>> iterableFuture = Futures.<Object>sequence(futures, aggregatorDispatcher);

        final Comparator<WithEntity> comparator;

        if (command instanceof SudoRetrieveThings) {
            comparator = (r1, r2) -> {
                final String r1ThingId = ((SudoRetrieveThingResponse) r1).getThing().getId().orElse(null);
                final String r2ThingId = ((SudoRetrieveThingResponse) r2).getThing().getId().orElse(null);
                return Integer.compare(thingIds.indexOf(r1ThingId), thingIds.indexOf(r2ThingId));
            };
        } else {
            comparator = (r1, r2) -> {
                final String r1ThingId = ((RetrieveThingResponse) r1).getThing().getId().orElse(null);
                final String r2ThingId = ((RetrieveThingResponse) r2).getThing().getId().orElse(null);
                return Integer.compare(thingIds.indexOf(r1ThingId), thingIds.indexOf(r2ThingId));
            };
        }

        final Future<CommandResponse> transformed =
                mapToReadCommandResponsesFuture(iterableFuture, comparator, command, traceContext);

        Patterns.pipe(transformed, aggregatorDispatcher).to(resultReceiver);
    }

    private Future<CommandResponse> mapToReadCommandResponsesFuture(
            final Future<Iterable<Object>> iterableFuture, final Comparator<WithEntity> comparator,
            final Command retrieveThings, final TraceContext traceContext) {
        return iterableFuture.map(new Mapper<Iterable<Object>, CommandResponse>() {
            @Override
            public CommandResponse apply(final Iterable<Object> p) {
                final List<JsonValue> things = StreamSupport.stream(p.spliterator(), false) //
                        .filter(obj -> obj instanceof WithEntity) //
                        .map(obj -> (WithEntity) obj) //
                        .sorted(comparator) //
                        .map(WithEntity::getEntity) //
                        .collect(toList());
                traceContext.addMetadata("count", Integer.toBinaryString(things.size()));
                traceContext.finish();

                if (retrieveThings instanceof SudoCommand) {
                    return SudoRetrieveThingsResponse.of(things.stream().collect(JsonCollectors.valuesToArray()),
                            retrieveThings.getDittoHeaders());
                } else {
                    final Optional<String> namespace = ((RetrieveThings) retrieveThings).getNamespace();
                    return RetrieveThingsResponse.of(things.stream().collect(JsonCollectors.valuesToArray()),
                            namespace.orElse(null), retrieveThings.getDittoHeaders());
                }
            }
        }, aggregatorDispatcher);
    }

    private Future<Object> askTargetActor(final Command command) {
        return Patterns.ask(targetActor, command, Timeout.apply(retrieveSingleThingTimeout.toMillis(),
                TimeUnit.MILLISECONDS));
    }

}
