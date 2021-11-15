/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.aggregator;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingsResponse;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingsResponse;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.PFBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.stream.Materializer;
import akka.stream.SourceRef;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Acts as a client for {@code org.eclipse.ditto.concierge.service.starter.actors.ThingsAggregatorActor} which responds
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

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final ActorRef targetActor;
    private final Materializer materializer;

    @SuppressWarnings("unused")
    private ThingsAggregatorProxyActor(final ActorRef targetActor) {
        this.targetActor = targetActor;
        materializer = Materializer.createMaterializer(this::getContext);
    }

    /**
     * Creates Akka configuration object Props for this ThingsAggregatorProxyActor.
     *
     * @param targetActor the Actor to delegate "asks" for the aggregation to.
     * @return the Akka configuration Props object
     */
    public static Props props(final ActorRef targetActor) {

        return Props.create(ThingsAggregatorProxyActor.class, targetActor);
    }

    private static void stopTimer(final StartedTimer timer) {
        timer.stop(); // stop timer
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
        final List<ThingId> thingIds = rt.getEntityIds();
        log.withCorrelationId(rt)
                .info("Got '{}' message. Retrieving requested '{}' Things..",
                        RetrieveThings.class.getSimpleName(), thingIds.size());

        final ActorRef sender = getSender();
        askTargetActor(rt, thingIds, msgToAsk, sender);
    }

    private void handleSudoRetrieveThings(final SudoRetrieveThings srt, final Object msgToAsk) {
        final List<ThingId> thingIds = srt.getThingIds();
        log.withCorrelationId(srt)
                .info("Got '{}' message. Retrieving requested '{}' Things..",
                        SudoRetrieveThings.class.getSimpleName(), thingIds.size());

        final ActorRef sender = getSender();
        askTargetActor(srt, thingIds, msgToAsk, sender);
    }

    private void askTargetActor(final Command<?> command, final List<ThingId> thingIds,
            final Object msgToAsk, final ActorRef sender) {
        Patterns.ask(targetActor, msgToAsk, Duration.ofSeconds(ASK_TIMEOUT))
                .thenAccept(response -> {
                    if (response instanceof SourceRef) {
                        handleSourceRef((SourceRef<?>) response, thingIds, command, sender);
                    } else if (response instanceof DittoRuntimeException) {
                        sender.tell(response, getSelf());
                    } else {
                        log.error("Unexpected non-DittoRuntimeException error - responding with " +
                                        "GatewayInternalErrorException. Cause: {} - {}",
                                response.getClass().getSimpleName(), response);
                        final GatewayInternalErrorException responseEx =
                                GatewayInternalErrorException.newBuilder()
                                        .dittoHeaders(command.getDittoHeaders())
                                        .build();
                        sender.tell(responseEx, getSelf());
                    }
                });
    }

    private void handleSourceRef(final SourceRef<?> sourceRef, final List<ThingId> thingIds,
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

        final StartedTimer timer = DittoMetrics.timer(TRACE_AGGREGATOR_RETRIEVE_THINGS)
                .tag("size", Integer.toString(thingIds.size()))
                .start();
        DittoTracing.wrapTimer(DittoTracing.extractTraceContext(originatingCommand), timer);

        final Source<Jsonifiable<?>, NotUsed> thingNotAccessibleExceptionSource = Source.single(
                ThingNotAccessibleException.fromMessage("Thing could not be accessed.", DittoHeaders.empty())
        );

        final CompletionStage<List<PlainJson>> o =
                sourceRef.getSource()
                        .<Jsonifiable<?>>map(Jsonifiable.class::cast)
                        .orElse(thingNotAccessibleExceptionSource)
                        .filterNot(DittoRuntimeException.class::isInstance)
                        .map(thingPlainJsonSupplier::apply)
                        .log("retrieve-thing-response", log)
                        .recoverWithRetries(1, new PFBuilder<Throwable, Source<PlainJson, NotUsed>>()
                                .match(NoSuchElementException.class, nsee -> Source.single(PlainJson.empty()))
                                .build()
                        )
                        .runWith(Sink.seq(), materializer);

        final CompletionStage<? extends CommandResponse<?>> commandResponseCompletionStage = o
                .thenApply(plainJsonSorter)
                .thenApply(overallResponseSupplier::apply)
                .thenApply(list -> {
                    stopTimer(timer);
                    return list;
                });

        Patterns.pipe(commandResponseCompletionStage, getContext().dispatcher()).to(originatingSender);
    }

    private Function<Jsonifiable<?>, PlainJson> supplyPlainJsonFromRetrieveThingResponse() {
        return jsonifiable -> {
            if (jsonifiable instanceof RetrieveThingResponse) {
                final RetrieveThingResponse response = (RetrieveThingResponse) jsonifiable;
                final String json = response.getEntityPlainString().orElseGet(() ->
                        response.getEntity(response.getImplementedSchemaVersion()).toString());
                return PlainJson.of(response.getEntityId(), json);
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
                return response.getThing().getEntityId()
                        .map(thingId -> PlainJson.of(thingId, json))
                        .orElse(null);
            } else {
                return null;
            }
        };
    }

    private Function<List<PlainJson>, List<PlainJson>> supplyPlainJsonSorter(final List<ThingId> thingIds) {
        return plainJsonThings -> {
            final Comparator<PlainJson> comparator = (pj1, pj2) -> {
                if (!pj1.isEmpty() && !pj2.isEmpty()) {
                    final ThingId thingId1 = ThingId.of(pj1.getId());
                    final ThingId thingId2 = ThingId.of(pj2.getId());
                    return Integer.compare(thingIds.indexOf(thingId1), thingIds.indexOf(thingId2));
                } else {
                    return 0;
                }
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
                .filter(Predicate.not(String::isEmpty))
                .collect(Collectors.toList()), namespace, dittoHeaders);
    }

    private Function<List<PlainJson>, CommandResponse<?>> supplySudoRetrieveThingsResponse(
            final DittoHeaders dittoHeaders) {
        return plainJsonThings -> SudoRetrieveThingsResponse.of(plainJsonThings.stream()
                .map(PlainJson::getJson)
                .filter(Predicate.not(String::isEmpty))
                .collect(Collectors.toList()), dittoHeaders);
    }

    /**
     * Internal representation of an entity's JSON string.
     */
    private static final class PlainJson {

        private final String id;
        private final String json;

        private PlainJson(final CharSequence id, final String json) {
            this.id = checkNotNull(id, "ID").toString();
            this.json = checkNotNull(json, "JSON");
        }

        static PlainJson empty() {
            return new PlainJson("", "");
        }

        static PlainJson of(final CharSequence id, final String json) {
            return new PlainJson(id, json);
        }

        boolean isEmpty() {
            return id.isEmpty() && json.isEmpty();
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
                    "id=" + id +
                    ", json=" + json +
                    "]";
        }

    }

}
