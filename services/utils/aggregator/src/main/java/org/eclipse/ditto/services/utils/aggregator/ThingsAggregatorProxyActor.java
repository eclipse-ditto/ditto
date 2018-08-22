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
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingsResponse;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
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
import akka.japi.pf.PFBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.PatternsCS;
import akka.stream.ActorMaterializer;
import akka.stream.SourceRef;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.Timeout;

/**
 * Acts as a client for {@code org.eclipse.ditto.services.concierge.starter.actors.ThingsAggregatorActor} which responds
 * to a {@link RetrieveThings} command via a {@link SourceRef} which is a pointer in the cluster emitting the retrieved
 * {@link Thing}s one after one in a stream. That ensures that the cluster messages size must not be increased when
 * streaming a larger amount of Things in the cluster.
 */
public final class ThingsAggregatorProxyActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "aggregatorProxy";

    private static final String TRACE_AGGREGATOR_RETRIEVE_THINGS = "aggregatorproxy_retrievethings";

    private static final int ASK_TIMEOUT = 60;

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
        PatternsCS.ask(targetActor, msgToAsk, Timeout.apply(ASK_TIMEOUT, TimeUnit.SECONDS))
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
        PatternsCS.ask(targetActor, msgToAsk, Timeout.apply(ASK_TIMEOUT, TimeUnit.SECONDS))
                .thenAccept(sourceRef ->
                        handleSourceRef(
                                (SourceRef) sourceRef, rt.getThingIds(), rt, sender, actorMaterializer)
                );
    }

    private void handleSourceRef(final SourceRef sourceRef, final List<String> thingIds,
            final Command<?> originatingCommand, final ActorRef originatingSender,
            final ActorMaterializer actorMaterializer) {

        final Comparator<JsonValue> comparator = (t1, t2) -> {
            final String thingId1 = t1.asObject().getValue(Thing.JsonFields.ID).orElse(null);
            final String thingId2 = t2.asObject().getValue(Thing.JsonFields.ID).orElse(null);
            return Integer.compare(thingIds.indexOf(thingId1), thingIds.indexOf(thingId2));
        };

        final Function<Jsonifiable<?>, List<JsonValue>> firstThingListSupplier;
        final Function<List<JsonValue>, CommandResponse<?>> overallResponseSupplier;

        if (originatingCommand instanceof SudoRetrieveThings) {
            firstThingListSupplier = supplyInitialThingListFromSudoRetrieveThingResponse();
            overallResponseSupplier = supplySudoRetrieveThingsResponse(
                    originatingCommand.getDittoHeaders()
            );
        } else {
            firstThingListSupplier = supplyInitialThingListFromRetrieveThingResponse();
            overallResponseSupplier = supplyRetrieveThingsResponse(
                    originatingCommand.getDittoHeaders(),
                    ((RetrieveThings) originatingCommand).getNamespace().orElse(null)
            );
        }

        final StartedTimer timer = DittoMetrics.expiringTimer(TRACE_AGGREGATOR_RETRIEVE_THINGS)
                .tag("size", Integer.toString(thingIds.size()))
                .build();

        final CompletionStage<?> o = (CompletionStage<?>) sourceRef.getSource()
                .orElse(Source.single(ThingNotAccessibleException.newBuilder("").build()))
                .map(firstThingListSupplier)
                .log("retrieve-thing-response", log)
                .recover(new PFBuilder()
                        .match(NoSuchElementException.class,
                                nsee -> overallResponseSupplier.apply(Collections.emptyList()))
                        .build()
                )
                .reduce((a, b) -> mergeLists((List<JsonValue>) a, (List<JsonValue>) b, comparator))
                .map(overallResponseSupplier)
                .via(stopTimer(timer))
                .runWith(Sink.last(), actorMaterializer);

        PatternsCS.pipe(o, getContext().dispatcher())
                .to(originatingSender);
    }

    private Function<Jsonifiable<?>, List<JsonValue>> supplyInitialThingListFromRetrieveThingResponse() {
        return retrieveThingResponse -> retrieveThingResponse instanceof RetrieveThingResponse ?
                Collections.singletonList(((RetrieveThingResponse) retrieveThingResponse)
                        .getEntity(retrieveThingResponse.getImplementedSchemaVersion())) :
                Collections.emptyList();
    }

    private Function<Jsonifiable<?>, List<JsonValue>> supplyInitialThingListFromSudoRetrieveThingResponse() {
        return sudoRetrieveThingResponse -> sudoRetrieveThingResponse instanceof SudoRetrieveThingResponse ?
                Collections.singletonList(((SudoRetrieveThingResponse) sudoRetrieveThingResponse)
                        .getEntity(sudoRetrieveThingResponse.getImplementedSchemaVersion())) :
                Collections.emptyList();
    }

    private Function<List<JsonValue>, CommandResponse<?>> supplyRetrieveThingsResponse(
            final DittoHeaders dittoHeaders,
            @Nullable final String namespace) {
        return things -> RetrieveThingsResponse.of(things.stream().collect(JsonCollectors.valuesToArray()),
                namespace,
                dittoHeaders);
    }

    private Function<List<JsonValue>, CommandResponse<?>> supplySudoRetrieveThingsResponse(
            final DittoHeaders dittoHeaders) {
        return things -> SudoRetrieveThingsResponse.of(things.stream().collect(JsonCollectors.valuesToArray()),
                dittoHeaders);
    }

    private static List<JsonValue> mergeLists(final List<JsonValue> first,
            final List<JsonValue> second, final Comparator<JsonValue> comparator) {

        final List<JsonValue> arrayList = new ArrayList<>(first);
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
