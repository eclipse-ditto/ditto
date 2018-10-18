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
package org.eclipse.ditto.services.utils.aggregator;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
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

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.PFBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.PatternsCS;
import akka.stream.ActorMaterializer;
import akka.stream.SourceRef;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

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
        PatternsCS.ask(targetActor, msgToAsk, Duration.ofSeconds(ASK_TIMEOUT))
                .thenAccept(sourceRef ->
                        handleSourceRef((SourceRef) sourceRef, rt.getThingIds(), rt, sender)
                );
    }

    private void handleSudoRetrieveThings(final SudoRetrieveThings rt, final Object msgToAsk) {
        LogUtil.enhanceLogWithCorrelationId(log, rt.getDittoHeaders().getCorrelationId());
        log.info("Got '{}' message. Retrieving requested '{}' Things..",
                SudoRetrieveThings.class.getSimpleName(),
                rt.getThingIds().size());

        final ActorRef sender = getSender();
        PatternsCS.ask(targetActor, msgToAsk, Duration.ofSeconds(ASK_TIMEOUT))
                .thenAccept(sourceRef ->
                        handleSourceRef((SourceRef) sourceRef, rt.getThingIds(), rt, sender)
                );
    }

    private void handleSourceRef(final SourceRef sourceRef, final List<String> thingIds,
            final Command<?> originatingCommand, final ActorRef originatingSender) {
        final Function<Jsonifiable<?>, PlainJson> thingPlainJsonSupplier;
        final Function<List<PlainJson>, CommandResponse<?>> overallResponseSupplier;
        final Function<List<PlainJson>, List<PlainJson>> plainJsonSorter = supplyPlainJsonSorter(thingIds);

        if (originatingCommand instanceof SudoRetrieveThings) {
            thingPlainJsonSupplier = supplyPlainJsonFromSudoRetrieveThingResponse();
            overallResponseSupplier = supplySudoRetrieveThingsResponse(originatingCommand.getDittoHeaders());
        } else {
            thingPlainJsonSupplier = supplyPlainJsonFromRetrieveThingResponse();
            final String namespace = ((RetrieveThings) originatingCommand).getNamespace().orElse(null);
            overallResponseSupplier = supplyRetrieveThingsResponse(originatingCommand.getDittoHeaders(), namespace);
        }

        final StartedTimer timer = DittoMetrics.expiringTimer(TRACE_AGGREGATOR_RETRIEVE_THINGS)
                .tag("size", Integer.toString(thingIds.size()))
                .build();

        final CompletionStage<List<PlainJson>> o =
                (CompletionStage<List<PlainJson>>) sourceRef.getSource()
                        .orElse(Source.single(ThingNotAccessibleException.newBuilder("").build()))
                        .filterNot(el -> el instanceof DittoRuntimeException)
                        .map(param -> thingPlainJsonSupplier.apply((Jsonifiable<?>) param))
                        .log("retrieve-thing-response", log)
                        .recover(new PFBuilder()
                                .match(NoSuchElementException.class,
                                        nsee -> overallResponseSupplier.apply(Collections.emptyList()))
                                .build()
                        )
                        .runWith(Sink.seq(), actorMaterializer);

        final CompletionStage<? extends CommandResponse<?>> commandResponseCompletionStage = o
                .thenApply(plainJsonSorter)
                .thenApply(overallResponseSupplier::apply)
                .thenApply(list -> {
                    stopTimer(timer);
                    return list;
                });

        PatternsCS.pipe(commandResponseCompletionStage, getContext().dispatcher()).to(originatingSender);
    }

    private Function<Jsonifiable<?>, PlainJson> supplyPlainJsonFromRetrieveThingResponse() {
        return jsonifiable -> {
            if (jsonifiable instanceof RetrieveThingResponse) {
                final RetrieveThingResponse response = (RetrieveThingResponse) jsonifiable;
                final String json = response.getEntityPlainString().orElseGet(() ->
                        response.getEntity(response.getImplementedSchemaVersion()).toString());
                return PlainJson.of(response.getId(), json);
            } else {
                return null;
            }
        };
    }

    private Function<Jsonifiable<?>, PlainJson> supplyPlainJsonFromSudoRetrieveThingResponse() {
        return jsonifiable -> {
            if (jsonifiable instanceof SudoRetrieveThingResponse) {
                final SudoRetrieveThingResponse response = (SudoRetrieveThingResponse) jsonifiable;
                final String json = response.getEntityPlainString().orElseGet(() ->
                        response.getEntity(response.getImplementedSchemaVersion()).toString());
                return PlainJson.of(response.getId(), json);
            } else {
                return null;
            }
        };
    }

    private Function<List<PlainJson>, List<PlainJson>> supplyPlainJsonSorter(final List<String> thingIds) {
        return plainJsonThings -> {
            final Comparator<PlainJson> comparator = (pj1, pj2) -> {
                final String thingId1 = pj1.getId();
                final String thingId2 = pj2.getId();
                return Integer.compare(thingIds.indexOf(thingId1), thingIds.indexOf(thingId2));
            };

            final List<PlainJson> sortedList = new ArrayList<>(plainJsonThings);
            sortedList.sort(comparator);
            return sortedList;
        };
    }

    private Function<List<PlainJson>, CommandResponse<?>> supplyRetrieveThingsResponse(
            final DittoHeaders dittoHeaders,
            @Nullable final String namespace) {
        return plainJsonThings -> RetrieveThingsResponse.of(plainJsonThings.stream()
                .map(PlainJson::getJson)
                .collect(Collectors.toList()), namespace, dittoHeaders);
    }

    private Function<List<PlainJson>, CommandResponse<?>> supplySudoRetrieveThingsResponse(
            final DittoHeaders dittoHeaders) {
        return plainJsonThings -> SudoRetrieveThingsResponse.of(plainJsonThings.stream()
                .map(PlainJson::getJson)
                .collect(Collectors.toList()), dittoHeaders);
    }

    private static void stopTimer(final StartedTimer timer) {
        timer.stop(); // stop timer
    }

    /**
     * Internal representation of an entity's JSON string.
     */
    private static final class PlainJson {

        private final String id;
        private final String json;

        private PlainJson(final String id, final String json) {
            this.id = id;
            this.json = json;
        }

        static PlainJson of(final String id, final String json) {
            checkNotNull(id, "ID");
            checkNotNull(json, "JSON");
            return new PlainJson(id, json);
        }

        String getId() {
            return id;
        }

        String getJson() {
            return json;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final PlainJson plainJson = (PlainJson) o;
            return Objects.equals(id, plainJson.id) &&
                    Objects.equals(json, plainJson.json);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, json);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    ", id=" + id +
                    ", json=" + json +
                    "]";
        }

    }

}
