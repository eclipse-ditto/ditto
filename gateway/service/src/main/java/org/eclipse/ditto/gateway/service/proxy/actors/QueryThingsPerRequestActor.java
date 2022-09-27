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
package org.eclipse.ditto.gateway.service.proxy.actors;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.gateway.service.util.config.endpoints.HttpConfig;
import org.eclipse.ditto.internal.utils.akka.actors.AbstractActorWithShutdownBehavior;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingsResponse;
import org.eclipse.ditto.thingsearch.api.events.ThingsOutOfSync;
import org.eclipse.ditto.thingsearch.model.SearchModelFactory;
import org.eclipse.ditto.thingsearch.model.SearchResult;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThingsResponse;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.CoordinatedShutdown;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.pattern.Patterns;

/**
 * Actor which is started for each {@link QueryThings} command in the gateway handling the response from
 * "things-search", retrieving the found things from "things" via the {@code aggregatorProxyActor} and responding to the
 * {@code originatingSender} with the combined result.
 * <p>
 * This is needed in gateway so that we can maintain the max. cluster-message size in Ditto while still being able to
 * respond to searches with max. 200 search results.
 * </p>
 */
final class QueryThingsPerRequestActor extends AbstractActorWithShutdownBehavior {

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final QueryThings queryThings;
    private final ActorRef commandForwarderActor;
    private final ActorRef originatingSender;
    private final ActorRef pubSubMediator;

    @Nullable private QueryThingsResponse queryThingsResponse;
    @Nullable private List<ThingId> queryThingsResponseThingIds;
    @Nullable private Cancellable cancellableShutdownTask;
    private boolean inCoordinatedShutdown;
    @Nullable private ActorRef coordinatedShutdownSender;

    @SuppressWarnings("unused")
    private QueryThingsPerRequestActor(final QueryThings queryThings,
            final ActorRef commandForwarderActor,
            final ActorRef originatingSender,
            final ActorRef pubSubMediator,
            final HttpConfig httpConfig) {

        this.queryThings = queryThings;
        this.commandForwarderActor = commandForwarderActor;
        this.originatingSender = originatingSender;
        this.pubSubMediator = pubSubMediator;
        queryThingsResponse = null;
        cancellableShutdownTask = null;
        inCoordinatedShutdown = false;
        coordinatedShutdownSender = null;

        getContext().setReceiveTimeout(httpConfig.getRequestTimeout());
    }

    /**
     * Creates Akka configuration object Props for this QueryThingsPerRequestActor.
     *
     * @return the Akka configuration Props object.
     */
    static Props props(final QueryThings queryThings,
            final ActorRef commandForwarderActor,
            final ActorRef originatingSender,
            final ActorRef pubSubMediator,
            final HttpConfig httpConfig) {

        return Props.create(QueryThingsPerRequestActor.class, queryThings, commandForwarderActor, originatingSender,
                pubSubMediator, httpConfig);
    }

    @Override
    public void preStart() {
        final var coordinatedShutdown = CoordinatedShutdown.get(getContext().getSystem());

        final var serviceRequestsDoneTask = "service-requests-done-query-things-actor" ;
        cancellableShutdownTask = coordinatedShutdown.addCancellableTask(CoordinatedShutdown.PhaseServiceRequestsDone(),
                serviceRequestsDoneTask,
                () -> Patterns.ask(getSelf(), Control.SERVICE_REQUESTS_DONE, SHUTDOWN_ASK_TIMEOUT)
                        .thenApply(reply -> Done.done())
        );
    }

    @Override
    public void postStop() {
        if (cancellableShutdownTask != null) {
            cancellableShutdownTask.cancel();
        }
    }

    @Override
    public Receive handleMessage() {
        return receiveBuilder()
                .match(ReceiveTimeout.class, receiveTimeout -> {
                    log.debug("Got ReceiveTimeout");
                    stopMyself();
                })
                .match(QueryThingsResponse.class, qtr -> {
                    queryThingsResponse = qtr;

                    log.withCorrelationId(qtr)
                            .debug("Received QueryThingsResponse: {}", qtr);

                    queryThingsResponseThingIds = qtr.getSearchResult()
                            .stream()
                            .flatMap(val -> val.asObject().getValue(Thing.JsonFields.ID).stream())
                            .map(ThingId::of)
                            .toList();

                    if (queryThingsResponseThingIds.isEmpty() || queryThingsOnlyContainsThingIdSelector()) {
                        // shortcuts: we don't have to look up the things
                        // - for no search results
                        // - if only the "thingId" was selected in the QueryThings commands
                        originatingSender.tell(qtr, getSelf());
                        stopMyself();
                    } else {
                        final Optional<JsonFieldSelector> selectedFieldsWithThingId = getSelectedFieldsWithThingId();
                        final RetrieveThings retrieveThings = RetrieveThings.getBuilder(queryThingsResponseThingIds)
                                .dittoHeaders(qtr.getDittoHeaders().toBuilder().responseRequired(true).build())
                                .selectedFields(selectedFieldsWithThingId)
                                .build();
                        // delegate to the ThingsAggregatorProxyActor via the command forwarder -
                        // which receives the results via a cluster stream:
                        commandForwarderActor.tell(retrieveThings, getSelf());
                    }
                })
                .match(RetrieveThingsResponse.class, rtr -> {
                    log.withCorrelationId(rtr)
                            .debug("Received RetrieveThingsResponse: {}", rtr);

                    if (queryThingsResponse != null) {
                        final JsonArray rtrEntity = rtr.getEntity(rtr.getImplementedSchemaVersion()).asArray();
                        final JsonArray retrievedEntitiesWithFieldSelection = getEntitiesWithSelectedFields(rtrEntity);
                        final SearchResult resultWithRetrievedItems = SearchModelFactory.newSearchResultBuilder()
                                .addAll(retrievedEntitiesWithFieldSelection)
                                .nextPageOffset(queryThingsResponse.getSearchResult().getNextPageOffset().orElse(null))
                                .cursor(queryThingsResponse.getSearchResult().getCursor().orElse(null))
                                .build();
                        final QueryThingsResponse theQueryThingsResponse =
                                QueryThingsResponse.of(resultWithRetrievedItems, rtr.getDittoHeaders());
                        originatingSender.tell(theQueryThingsResponse, getSelf());
                        notifyOutOfSyncThings(rtrEntity);
                    } else {
                        log.warning("Did not receive a QueryThingsResponse when a RetrieveThingsResponse occurred: {}",
                                rtr);
                    }

                    stopMyself();
                })
                .matchAny(any -> {
                    // all other messages (e.g. DittoRuntimeExceptions) are directly returned to the sender:
                    originatingSender.tell(any, getSender());
                    stopMyself();
                })
                .build();
    }

    @Override
    public void serviceUnbind(final Control serviceUnbind) {
        // nothing to do
    }

    @Override
    public void serviceRequestsDone(final Control serviceRequestsDone) {
        log.info("{}: waiting to complete the request", serviceRequestsDone);
        inCoordinatedShutdown = true;
        coordinatedShutdownSender = getSender();
    }

    private boolean queryThingsOnlyContainsThingIdSelector() {
        final Optional<JsonFieldSelector> fields = queryThings.getFields();
        return fields.isPresent() && fields.get().getPointers()
                .equals(Set.of(JsonPointer.of(Thing.JsonFields.ID.getPointer())));
    }

    /**
     * Extracts selected fields from {@link #queryThings} and ensures that the Thing ID is one of those fields.
     * If no fields are selected, this means that all fields should be returned.
     *
     * @return the optional field selector.
     */
    private Optional<JsonFieldSelector> getSelectedFieldsWithThingId() {
        return queryThings.getFields()
                .filter(fields -> !fields.getPointers().contains(Thing.JsonFields.ID.getPointer()))
                .map(jsonFieldSelector -> JsonFieldSelector.newInstance(
                        Thing.JsonFields.ID.getPointer(),
                        jsonFieldSelector.getPointers().toArray(new JsonPointer[0])))
                .or(queryThings::getFields);
    }

    /**
     * Maps the retrieved entities into entities with the originally selected fields.
     *
     * @param retrievedEntities the retrieved entities.
     * @return the retrieved entities with the originally selected fields.
     */
    private JsonArray getEntitiesWithSelectedFields(final JsonArray retrievedEntities) {
        return queryThings.getFields()
                .filter(fields -> !fields.getPointers().contains(Thing.JsonFields.ID.getPointer()))
                .map(fields -> retrievedEntities.stream()
                        .map(jsonValue -> jsonValue.asObject().get(fields))
                        .collect(JsonCollectors.valuesToArray())
                )
                .orElse(retrievedEntities);
    }

    /**
     * Publish an UpdateThings command including thing IDs in QueryThingsResponse but not in results with retrieved
     * items.
     *
     * @param rtrEntity entity of the RetrieveThingsResponse from the aggregator actor.
     * @throws NullPointerException if this.queryThingsResponse or this.queryThingsResponseThingIds is null.
     */
    private void notifyOutOfSyncThings(final JsonArray rtrEntity) {

        final Set<ThingId> retrievedThingIds = rtrEntity.stream()
                .filter(JsonValue::isObject)
                .flatMap(item -> item.asObject().getValue(Thing.JsonFields.ID).stream())
                .map(ThingId::of)
                .collect(Collectors.toSet());

        final Collection<ThingId> outOfSyncThingIds = queryThingsResponseThingIds.stream()
                .filter(thingId -> !retrievedThingIds.contains(thingId))
                .toList();

        if (!outOfSyncThingIds.isEmpty()) {
            final ThingsOutOfSync thingsOutOfSync =
                    ThingsOutOfSync.of(outOfSyncThingIds, queryThings.getDittoHeaders());
            pubSubMediator.tell(DistPubSubAccess.publishViaGroup(ThingsOutOfSync.TYPE, thingsOutOfSync),
                    ActorRef.noSender());
        }
    }

    private void stopMyself() {
        if (inCoordinatedShutdown && coordinatedShutdownSender != null) {
            // complete coordinated shutdown phase - ServiceRequestsDone
            coordinatedShutdownSender.tell(Done.getInstance(), ActorRef.noSender());
        }

        getContext().cancelReceiveTimeout();
        getContext().stop(getSelf());
        inCoordinatedShutdown = false;
    }

}
