/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.search;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.akka.controlflow.ResumeSource;
import org.eclipse.ditto.internal.utils.akka.controlflow.ResumeSourceBuilder;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.StreamThings;
import org.eclipse.ditto.thingsearch.model.signals.events.ThingsOutOfSync;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.japi.Pair;
import akka.japi.pf.PFBuilder;
import akka.pattern.Patterns;
import akka.stream.Graph;
import akka.stream.RemoteStreamRefActorTerminatedException;
import akka.stream.SourceRef;
import akka.stream.SourceShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;

/**
 * Source of search results for one query.
 */
public final class SearchSource {

    private static final ThreadSafeDittoLogger LOGGER = DittoLoggerFactory.getThreadSafeLogger(SearchSource.class);

    private final ActorRef pubSubMediator;
    private final ActorSelection conciergeForwarder;
    private final Duration thingsAskTimeout;
    private final Duration searchAskTimeout;
    @Nullable private final JsonFieldSelector fields;
    private final JsonFieldSelector sortFields;
    private final StreamThings streamThings;
    private final boolean thingIdOnly;
    private final String lastThingId;

    SearchSource(final ActorRef pubSubMediator,
            final ActorSelection conciergeForwarder,
            final Duration thingsAskTimeout,
            final Duration searchAskTimeout,
            @Nullable final JsonFieldSelector fields,
            final JsonFieldSelector sortFields,
            final StreamThings streamThings,
            final String lastThingId) {
        this.pubSubMediator = pubSubMediator;
        this.conciergeForwarder = conciergeForwarder;
        this.thingsAskTimeout = thingsAskTimeout;
        this.searchAskTimeout = searchAskTimeout;
        this.fields = fields;
        this.sortFields = sortFields;
        this.streamThings = streamThings;
        this.thingIdOnly = fields != null && fields.getSize() == 1 &&
                fields.getPointers().contains(Thing.JsonFields.ID.getPointer());
        this.lastThingId = lastThingId;
    }

    /**
     * Create a new builder.
     *
     * @return a new builder.
     */
    public static SearchSourceBuilder newBuilder() {
        return new SearchSourceBuilder();
    }

    /**
     * Start a source of search results.
     *
     * @return the source of search results.
     */
    public Source<JsonObject, NotUsed> start(final Consumer<ResumeSourceBuilder<?, ?>> configurer) {

        return startAsPair(configurer).map(Pair::second);
    }

    /**
     * Start a robust source of search results paired with their IDs.
     *
     * @param configurer consumer to configure the resume source.
     * @return source of pair of ID-result pairs where the ID can be used for resumption.
     */
    public Source<Pair<String, JsonObject>, NotUsed> startAsPair(final Consumer<ResumeSourceBuilder<?, ?>> configurer) {
        // start with a ResumeSourceBuilder with useful defaults.
        final ResumeSourceBuilder<String, Pair<String, JsonObject>> builder =
                ResumeSource.<String, Pair<String, JsonObject>>newBuilder()
                        .minBackoff(Duration.ofSeconds(1L))
                        .maxBackoff(Duration.ofSeconds(20L))
                        .recovery(Duration.ofSeconds(60L))
                        .maxRestarts(25)
                        .initialSeed(lastThingId)
                        .resume(this::resume)
                        .nextSeed(this::nextSeed)
                        .mapError(this::mapError);
        configurer.accept(builder);
        return builder.build();
    }

    /**
     * Decide whether an error is recoverable.
     *
     * @param error error from upstream.
     * @return an empty optional if the error is recoverable, or a DittoRuntimeException if the error is not
     * recoverable.
     */
    private Optional<Throwable> mapError(final Throwable error) {
        if (error instanceof RemoteStreamRefActorTerminatedException) {
            LOGGER.withCorrelationId(streamThings).info("Resuming from: {}", error.toString());
            return Optional.empty();
        } else {
            return Optional.of(DittoRuntimeException.asDittoRuntimeException(error, e -> {
                LOGGER.withCorrelationId(streamThings).error("Unexpected error", e);
                return GatewayInternalErrorException.newBuilder().build();
            }));
        }
    }

    private Source<Pair<String, JsonObject>, NotUsed> resume(final String lastThingId) {
        return streamThingsFrom(lastThingId)
                .mapAsync(1, streamThings -> Patterns.ask(conciergeForwarder, streamThings, searchAskTimeout))
                .via(expectMsgClass(SourceRef.class))
                .flatMapConcat(SourceRef::source)
                .flatMapConcat(thingId -> retrieveThingForElement((String) thingId));
    }

    private String nextSeed(final List<Pair<String, JsonObject>> finalElements) {
        return finalElements.isEmpty()
                ? lastThingId
                : finalElements.get(finalElements.size() - 1).first();
    }

    private Source<StreamThings, NotUsed> streamThingsFrom(final String lastThingId) {
        if (lastThingId.isEmpty()) {
            return Source.single(streamThings);
        } else {
            return retrieveSortValues(lastThingId).map(streamThings::setSortValues);
        }
    }

    private Source<JsonArray, NotUsed> retrieveSortValues(final String thingId) {
        return retrieveThing(thingId, sortFields)
                .map(thingJson -> sortFields.getPointers()
                        .stream()
                        .map(pointer -> thingJson.getValue(pointer).orElse(JsonFactory.nullLiteral()))
                        .collect(JsonCollectors.valuesToArray())
                );
    }

    private Source<Pair<String, JsonObject>, NotUsed> retrieveThingForElement(final String thingId) {
        if (thingIdOnly) {
            final JsonObject idOnlyThingJson = JsonObject.newBuilder().set(Thing.JsonFields.ID, thingId).build();
            return Source.single(Pair.create(thingId, idOnlyThingJson));
        } else {
            return retrieveThing(thingId, fields)
                    .map(thingJson -> Pair.create(thingId, thingJson))
                    .recoverWithRetries(1,
                            new PFBuilder<Throwable, Graph<SourceShape<Pair<String, JsonObject>>, NotUsed>>()
                                    .match(ThingNotAccessibleException.class, thingNotAccessible -> {
                                        // out-of-sync thing detected
                                        final ThingsOutOfSync thingsOutOfSync =
                                                ThingsOutOfSync.of(Collections.singletonList(ThingId.of(thingId)),
                                                        getDittoHeaders());

                                        pubSubMediator.tell(
                                                DistPubSubAccess.publishViaGroup(ThingsOutOfSync.TYPE, thingsOutOfSync),
                                                ActorRef.noSender());
                                        return Source.empty();
                                    })
                                    .build()
                    );
        }
    }

    private Source<JsonObject, NotUsed> retrieveThing(final String thingId,
            @Nullable final JsonFieldSelector selector) {
        final RetrieveThing retrieveThing = RetrieveThing.getBuilder(ThingId.of(thingId), getDittoHeaders())
                .withSelectedFields(selector)
                .build();

        final CompletionStage<Object> responseFuture =
                Patterns.ask(conciergeForwarder, retrieveThing, thingsAskTimeout);

        return Source.completionStage(responseFuture)
                .via(expectMsgClass(RetrieveThingResponse.class))
                .map(response -> response.getEntity().asObject());
    }

    private DittoHeaders getDittoHeaders() {
        return streamThings.getDittoHeaders();
    }

    private <T> Flow<Object, T, NotUsed> expectMsgClass(final Class<T> clazz) {
        return Flow.create()
                .flatMapConcat(element -> {
                    if (clazz.isInstance(element)) {
                        return Source.single(clazz.cast(element));
                    } else if (element instanceof Throwable) {
                        return Source.failed((Throwable) element);
                    } else {
                        final String message =
                                String.format("Expect <%s>, got <%s>", clazz.getCanonicalName(), element);
                        return Source.failed(new ClassCastException(message));
                    }
                });
    }

}
