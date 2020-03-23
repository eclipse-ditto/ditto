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
package org.eclipse.ditto.services.utils.search;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.akka.controlflow.ResumeSource;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.thingsearch.query.StreamThings;
import org.eclipse.ditto.signals.events.thingsearch.ThingsOutOfSync;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.japi.Pair;
import akka.japi.pf.PFBuilder;
import akka.pattern.Patterns;
import akka.stream.Graph;
import akka.stream.SourceRef;
import akka.stream.SourceShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;

/**
 * Source of search results for one query.
 */
public final class SearchSource {

    private final ActorRef pubSubMediator;
    private final ActorRef conciergeForwarder;
    private final Duration thingsAskTimeout;
    private final Duration searchAskTimeout;
    @Nullable private final JsonFieldSelector fields;
    private final JsonFieldSelector sortFields;
    private final StreamThings streamThings;
    private final Duration minBackoff;
    private final Duration maxBackoff;
    private final int maxRetries;
    private final Duration recovery;
    private final boolean thingIdOnly;
    private final String lastThingId;

    SearchSource(final ActorRef pubSubMediator,
            final ActorRef conciergeForwarder,
            final Duration thingsAskTimeout,
            final Duration searchAskTimeout,
            @Nullable final JsonFieldSelector fields,
            final JsonFieldSelector sortFields,
            final StreamThings streamThings,
            final Duration minBackoff,
            final Duration maxBackoff,
            final int maxRetries,
            final Duration recovery,
            final String lastThingId) {
        this.pubSubMediator = pubSubMediator;
        this.conciergeForwarder = conciergeForwarder;
        this.thingsAskTimeout = thingsAskTimeout;
        this.searchAskTimeout = searchAskTimeout;
        this.fields = fields;
        this.sortFields = sortFields;
        this.streamThings = streamThings;
        this.minBackoff = minBackoff;
        this.maxBackoff = maxBackoff;
        this.maxRetries = maxRetries;
        this.recovery = recovery;
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
     * Start a robust source of search results.
     *
     * @return the robust source of search results.
     */
    public Source<JsonObject, NotUsed> start() {
        return start(minBackoff, maxBackoff, maxRetries, recovery);
    }

    /**
     * Start a robust source of search results.
     *
     * @param minBackoff minimum backoff after a failure.
     * @param maxBackoff maximum backoff after a failure.
     * @param maxRetries how many retries to make before signaling failure downstream.
     * @param recovery interval between failures to reset backoff and retry counter.
     * @return the robust source of search results.
     */
    public Source<JsonObject, NotUsed> start(final Duration minBackoff,
            final Duration maxBackoff,
            final int maxRetries,
            final Duration recovery) {

        return startAsPair(minBackoff, maxBackoff, maxRetries, recovery).map(Pair::second);
    }

    /**
     * Start a robust source of search results paired with their IDs.
     *
     * @return source of pair of ID-result pairs where the ID can be used for resumption.
     */
    public Source<Pair<String, JsonObject>, NotUsed> startAsPair() {
        return startAsPair(minBackoff, maxBackoff, maxRetries, recovery);
    }

    private Source<Pair<String, JsonObject>, NotUsed> startAsPair(final Duration minBackoff,
            final Duration maxBackoff,
            final int maxRetries,
            final Duration recovery) {
        return ResumeSource.onFailureWithBackoff(
                minBackoff,
                maxBackoff,
                maxRetries,
                recovery,
                lastThingId,
                this::resume,
                1,
                this::nextSeed
        );
    }

    private String nextSeed(final List<Pair<String, JsonObject>> finalElements) {
        return finalElements.isEmpty()
                ? ""
                : finalElements.get(finalElements.size() - 1).first();
    }

    private Source<Pair<String, JsonObject>, NotUsed> resume(final String lastThingId) {
        return streamThingsFrom(lastThingId)
                .mapAsync(1, streamThings -> Patterns.ask(conciergeForwarder, streamThings, searchAskTimeout))
                .via(expectMsgClass(SourceRef.class))
                .flatMapConcat(SourceRef::source)
                .flatMapConcat(thingId -> retrieveThingForElement((String) thingId));
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

        return Source.fromCompletionStage(responseFuture)
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
