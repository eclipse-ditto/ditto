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
package org.eclipse.ditto.edge.service.dispatching;

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
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.WithEntity;
import org.eclipse.ditto.internal.utils.akka.actors.AbstractActorWithShutdownBehaviorAndRequestCounting;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.span.SpanOperationName;
import org.eclipse.ditto.internal.utils.tracing.span.StartedSpan;
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
 * Acts as a client for {@code ThingsAggregatorActor} which responds
 * to a {@link RetrieveThings} command via a {@link SourceRef} which is a pointer in the cluster emitting the retrieved
 * {@link Thing}s one after one in a stream. That ensures that the cluster messages size must not be increased when
 * streaming a larger amount of Things in the cluster.
 */
public final class ThingsAggregatorProxyActor extends AbstractActorWithShutdownBehaviorAndRequestCounting {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "aggregatorProxy";

    private static final SpanOperationName TRACE_AGGREGATOR_RETRIEVE_THINGS =
            SpanOperationName.of("aggregatorproxy_retrievethings");

    private static final int ASK_TIMEOUT = 60;

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final ActorRef pubSubMediator;
    private final Materializer materializer;

    @SuppressWarnings("unused")
    private ThingsAggregatorProxyActor(final ActorRef pubSubMediator) {
        this.pubSubMediator = pubSubMediator;
        materializer = Materializer.createMaterializer(this::getContext);
    }

    /**
     * Creates Akka configuration object Props for this ThingsAggregatorProxyActor.
     *
     * @param pubSubMediator the pub/sub mediator Actor ref to delegate "asks" for the aggregation to.
     * @return the Akka configuration Props object
     */
    public static Props props(final ActorRef pubSubMediator) {

        return Props.create(ThingsAggregatorProxyActor.class, pubSubMediator);
    }

    @Override
    public Receive handleMessage() {
        return ReceiveBuilder.create()
                .match(RetrieveThings.class, rt -> handleRetrieveThings(rt, rt))
                .match(SudoRetrieveThings.class, srt -> handleSudoRetrieveThings(srt, srt))
                .matchAny(m -> {
                    log.warning("Got unknown message: {}", m);
                    unhandled(m);
                })
                .build();
    }

    @Override
    public void serviceUnbind(final Control serviceUnbind) {
        // nothing to do
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

        final Object tracedMsgToAsk;
        final var startedSpan = DittoTracing.newPreparedSpan(
                        command.getDittoHeaders(),
                        TRACE_AGGREGATOR_RETRIEVE_THINGS
                )
                .tag("size", Integer.toString(thingIds.size()))
                .start();
        if (msgToAsk instanceof DittoHeadersSettable<?> dittoHeadersSettable) {
            tracedMsgToAsk = dittoHeadersSettable.setDittoHeaders(
                    DittoHeaders.of(startedSpan.propagateContext(dittoHeadersSettable.getDittoHeaders()))
            );
        } else {
            tracedMsgToAsk = msgToAsk;
        }

        final DistributedPubSubMediator.Publish pubSubMsg =
                DistPubSubAccess.publishViaGroup(command.getType(), tracedMsgToAsk);

        withRequestCounting(
                Patterns.ask(pubSubMediator, pubSubMsg, Duration.ofSeconds(ASK_TIMEOUT))
                        .thenAccept(response -> {
                            if (response instanceof SourceRef) {
                                handleSourceRef((SourceRef<?>) response, thingIds, command, sender, startedSpan);
                            } else if (response instanceof DittoRuntimeException dre) {
                                startedSpan.tagAsFailed(dre).finish();
                                sender.tell(response, getSelf());
                            } else {
                                log.error("Unexpected non-DittoRuntimeException error - responding with " +
                                                "DittoInternalErrorException. Cause: {} - {}",
                                        response.getClass().getSimpleName(), response);
                                final DittoInternalErrorException responseEx =
                                        DittoInternalErrorException.newBuilder()
                                                .dittoHeaders(command.getDittoHeaders())
                                                .build();
                                startedSpan.tagAsFailed(responseEx).finish();
                                sender.tell(responseEx, getSelf());
                            }
                        })
        );
    }

    private void handleSourceRef(final SourceRef<?> sourceRef, final List<ThingId> thingIds,
            final Command<?> originatingCommand, final ActorRef originatingSender, final StartedSpan startedSpan) {
        final Function<Jsonifiable<?>, PlainJson> thingPlainJsonSupplier;
        final Function<List<PlainJson>, CommandResponse<?>> overallResponseSupplier;
        final UnaryOperator<List<PlainJson>> plainJsonSorter = supplyPlainJsonSorter(thingIds);

        if (originatingCommand instanceof SudoRetrieveThings) {
            thingPlainJsonSupplier = supplyPlainJsonFromSudoRetrieveThingResponse();
            overallResponseSupplier = supplySudoRetrieveThingsResponse(originatingCommand.getDittoHeaders());
        } else {
            thingPlainJsonSupplier = supplyPlainJsonFromRetrieveThingResponse();
            final String namespace = ((RetrieveThings) originatingCommand).getNamespace().orElse(null);
            overallResponseSupplier = supplyRetrieveThingsResponse(originatingCommand.getDittoHeaders(), namespace);
        }

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
                    startedSpan.finish();
                    return list;
                });

        withRequestCounting(
                Patterns.pipe(commandResponseCompletionStage, getContext().dispatcher()).to(originatingSender)
                        .future().toCompletableFuture()
        );
    }

    private Function<Jsonifiable<?>, PlainJson> supplyPlainJsonFromRetrieveThingResponse() {
        return jsonifiable -> {
            if (jsonifiable instanceof RetrieveThingResponse response) {
                final String json = response.getEntityPlainString().orElseGet(() ->
                        response.getEntity(response.getImplementedSchemaVersion()).toString());

                return PlainJson.of(response.getEntityId(), json);
            } else if (jsonifiable instanceof WithEntity && jsonifiable instanceof WithEntityId) {
                final String json = ((WithEntity<?>) jsonifiable).getEntityPlainString().orElseGet(() ->
                        ((WithEntity<?>) jsonifiable).getEntity(jsonifiable.getImplementedSchemaVersion()).toString());

                return PlainJson.of(((WithEntityId) jsonifiable).getEntityId(), json);
            } else {
                return PlainJson.empty();
            }
        };
    }

    private Function<Jsonifiable<?>, PlainJson> supplyPlainJsonFromSudoRetrieveThingResponse() {
        return jsonifiable -> {
            if (jsonifiable instanceof SudoRetrieveThingResponse response) {
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

    private UnaryOperator<List<PlainJson>> supplyPlainJsonSorter(final List<ThingId> thingIds) {
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
                .toList(), namespace, dittoHeaders);
    }

    private Function<List<PlainJson>, CommandResponse<?>> supplySudoRetrieveThingsResponse(
            final DittoHeaders dittoHeaders) {

        return plainJsonThings -> SudoRetrieveThingsResponse.of(plainJsonThings.stream()
                .map(PlainJson::getJson)
                .filter(Predicate.not(String::isEmpty))
                .toList(), dittoHeaders);
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
