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
package org.eclipse.ditto.services.utils.aggregator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingsResponse;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.function.Function;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.PatternsCS;
import akka.stream.ActorMaterializer;
import akka.stream.SourceRef;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.util.Timeout;

/**
 * Acts as a client for {@code org.eclipse.ditto.services.concierge.starter.actors.ThingsAggregatorActor} which responds
 * to a {@link RetrieveThings} command via a {@link SourceRef} which is a pointer in the cluster emitting the retrieved
 * {@link Thing}s one after one in a stream.
 * <br/>
 * That ensures that the cluster messages size must not be increased when streaming a larger amount of Things in the
 * cluster.
 */
public final class ThingsAggregatorProxyActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "aggregatorProxy";

    private static final String TRACE_AGGREGATOR_RETRIEVE_THINGS = "aggregatorproxy_retrievethings";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef targetActor;
    private final ActorMaterializer actorMaterializer;

    private ThingsAggregatorProxyActor(final ActorRef targetActor) {
        this.targetActor = targetActor;
        actorMaterializer = ActorMaterializer.create(getContext());
    }

    /**
     * Creates Akka configuration object Props for this ThingsAggregatorProxyActor.
     *
     * @param targetActor the Actor to delegate "asks" for the aggregation to.
     * @return the Akka configuration Props object
     */
    public static Props props(final ActorRef targetActor) {
        return Props.create(ThingsAggregatorProxyActor.class, new Creator<ThingsAggregatorProxyActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ThingsAggregatorProxyActor create() {
                return new ThingsAggregatorProxyActor(targetActor);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveThings.class, rt -> handleRetrieveThings(rt, rt))
                .match(SudoRetrieveThings.class, srt -> handleSudoRetrieveThings(srt, srt))
                .match(DistributedPubSubMediator.Send.class, send -> {
                    final Object msg = send.msg();
                    if (msg instanceof RetrieveThings) {
                        handleRetrieveThings((RetrieveThings) msg, send);
                    } else if (msg instanceof SudoRetrieveThings) {
                        handleSudoRetrieveThings((SudoRetrieveThings) msg, send);
                    } else {
                        log.warning("Got unknown message: {}", send);
                        unhandled(send);
                    }
                })
                .matchAny(m -> {
                    log.warning("Got unknown message: {}", m);
                    unhandled(m);
                })
                .build();
    }

    private void handleRetrieveThings(final RetrieveThings rt, final Object msgToAsk) {
        LogUtil.enhanceLogWithCorrelationId(log, rt.getDittoHeaders().getCorrelationId());
        log.info("Got '{}' message. Retrieving requested '{}' Things..",
                RetrieveThings.class.getSimpleName(),
                rt.getThingIds().size());

        final ActorRef sender = getSender();
        PatternsCS.ask(targetActor, msgToAsk, Timeout.apply(30, TimeUnit.SECONDS))
                .thenAccept(sourceRef ->
                        handleSourceRef(
                                (SourceRef) sourceRef, rt.getThingIds(), rt, sender, actorMaterializer)
                );
    }

    private void handleSudoRetrieveThings(final SudoRetrieveThings rt, final Object msgToAsk) {
        LogUtil.enhanceLogWithCorrelationId(log, rt.getDittoHeaders().getCorrelationId());
        log.info("Got '{}' message. Retrieving requested '{}' Things..",
                SudoRetrieveThings.class.getSimpleName(),
                rt.getThingIds().size());

        final ActorRef sender = getSender();
        PatternsCS.ask(targetActor, msgToAsk, Timeout.apply(30, TimeUnit.SECONDS))
                .thenAccept(sourceRef ->
                        handleSourceRef(
                                (SourceRef) sourceRef, rt.getThingIds(), rt, sender, actorMaterializer)
                );
    }

    private void handleSourceRef(final SourceRef sourceRef, final List<String> thingIds,
            final Command<?> originatingCommand, final ActorRef originatingSender,
            final ActorMaterializer actorMaterializer) {

        final Comparator<Thing> comparator = (t1, t2) -> {
            final String r1ThingId = t1.getId().orElse(null);
            final String r2ThingId = t2.getId().orElse(null);
            return Integer.compare(thingIds.indexOf(r1ThingId), thingIds.indexOf(r2ThingId));
        };

        final Function<? extends CommandResponse<?>, List<Thing>> firstThingListSupplier;
        final Function<List<Thing>, CommandResponse<?>> overallResponseSupplier;

        if (originatingCommand instanceof SudoRetrieveThings) {
            firstThingListSupplier = supplyInitialThingListFromSudoRetrieveThingResponse();
            overallResponseSupplier = supplySudoRetrieveThingsResponse(
                    originatingCommand.getDittoHeaders(),
                    ((SudoRetrieveThings) originatingCommand).getSelectedFields().orElse(null)
            );
        } else {
            firstThingListSupplier = supplyInitialThingListFromRetrieveThingResponse();
            overallResponseSupplier = supplyRetrieveThingsResponse(
                    originatingCommand.getDittoHeaders(),
                    ((RetrieveThings) originatingCommand).getSelectedFields().orElse(null),
                    ((RetrieveThings) originatingCommand).getNamespace().orElse(null)
            );
        }

        final StartedTimer timer = DittoMetrics.expiringTimer(TRACE_AGGREGATOR_RETRIEVE_THINGS)
                .tag("size", Integer.toString(thingIds.size()))
                .build();

        final CompletionStage<?> o = (CompletionStage<?>) sourceRef.getSource()
                .map(firstThingListSupplier)
                .log("retrieve-thing-response", log)
                .reduce((a, b) -> mergeLists((List<Thing>) a, (List<Thing>) b, comparator))
                .map(overallResponseSupplier)
                .via(stopTimer(timer))
                .runWith(Sink.last(), actorMaterializer);

        PatternsCS.pipe(o, getContext().dispatcher())
                .to(originatingSender);
    }

    private Function<RetrieveThingResponse, List<Thing>> supplyInitialThingListFromRetrieveThingResponse() {
        return retrieveThingResponse -> Collections.singletonList((retrieveThingResponse).getThing());
    }

    private Function<SudoRetrieveThingResponse, List<Thing>> supplyInitialThingListFromSudoRetrieveThingResponse() {
        return sudoRetrieveThingResponse -> Collections.singletonList(sudoRetrieveThingResponse.getThing());
    }

    private Function<List<Thing>, CommandResponse<?>> supplyRetrieveThingsResponse(
            final DittoHeaders dittoHeaders,
            @Nullable final JsonFieldSelector selectedFields,
            @Nullable final String namespace) {
        return things -> RetrieveThingsResponse.of((List<Thing>) things,
                selectedFields,
                null,
                namespace,
                dittoHeaders);
    }

    private Function<List<Thing>, CommandResponse<?>> supplySudoRetrieveThingsResponse(
            final DittoHeaders dittoHeaders,
            @Nullable final JsonFieldSelector selectedFields) {
        return things -> SudoRetrieveThingsResponse.of((List<Thing>) things,
                selectedFields,
                null,
                dittoHeaders);
    }

    private static List<Thing> mergeLists(final List<Thing> first,
            final List<Thing> second, final Comparator<Thing> comparator) {

        final List<Thing> arrayList = new ArrayList<>(first);
        arrayList.addAll(second);
        arrayList.sort(comparator);

        return arrayList;
    }

    private static Flow<CommandResponse<?>, CommandResponse<?>, NotUsed> stopTimer(final StartedTimer timer) {
        return Flow.fromFunction(foo -> {
            timer.stop(); // stop timer
            return foo;
        });
    }
}
