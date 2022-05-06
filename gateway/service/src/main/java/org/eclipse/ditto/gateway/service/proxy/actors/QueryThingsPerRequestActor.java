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

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.model.SearchModelFactory;
import org.eclipse.ditto.thingsearch.model.SearchResult;
import org.eclipse.ditto.gateway.service.util.config.endpoints.GatewayHttpConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.HttpConfig;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingsResponse;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThingsResponse;
import org.eclipse.ditto.thingsearch.model.signals.events.ThingsOutOfSync;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;

/**
 * Actor which is started for each {@link QueryThings} command in the gateway handling the response from
 * "things-search", retrieving the found things from "things" via the {@code aggregatorProxyActor} and responding to the
 * {@code originatingSender} with the combined result.
 * <p>
 * This is needed in gateway so that we can maintain the max. cluster-message size in Ditto while still being able to
 * respond to searches with max. 200 search results.
 * </p>
 */
final class QueryThingsPerRequestActor extends AbstractActor {

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final QueryThings queryThings;
    private final ActorRef aggregatorProxyActor;
    private final ActorRef originatingSender;
    private final ActorRef pubSubMediator;

    private QueryThingsResponse queryThingsResponse;
    private List<ThingId> queryThingsResponseThingIds;

    @SuppressWarnings("unused")
    private QueryThingsPerRequestActor(final QueryThings queryThings,
            final ActorRef aggregatorProxyActor,
            final ActorRef originatingSender,
            final ActorRef pubSubMediator) {

        this.queryThings = queryThings;
        this.aggregatorProxyActor = aggregatorProxyActor;
        this.originatingSender = originatingSender;
        this.pubSubMediator = pubSubMediator;
        queryThingsResponse = null;

        final HttpConfig httpConfig = GatewayHttpConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );

        getContext().setReceiveTimeout(httpConfig.getRequestTimeout());
    }

    /**
     * Creates Akka configuration object Props for this QueryThingsPerRequestActor.
     *
     * @return the Akka configuration Props object.
     */
    static Props props(final QueryThings queryThings,
            final ActorRef aggregatorProxyActor,
            final ActorRef originatingSender,
            final ActorRef pubSubMediator) {

        return Props.create(QueryThingsPerRequestActor.class, queryThings, aggregatorProxyActor, originatingSender,
                pubSubMediator);
    }

    @Override
    public Receive createReceive() {
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
                            .collect(Collectors.toList());

                    if (queryThingsResponseThingIds.isEmpty()) {
                        // shortcut - for no search results we don't have to lookup the things
                        originatingSender.tell(qtr, getSelf());
                        stopMyself();
                    } else {
                        final Optional<JsonFieldSelector> selectedFieldsWithThingId = getSelectedFieldsWithThingId();
                        final RetrieveThings retrieveThings = RetrieveThings.getBuilder(queryThingsResponseThingIds)
                                .dittoHeaders(qtr.getDittoHeaders().toBuilder().responseRequired(true).build())
                                .selectedFields(selectedFieldsWithThingId)
                                .build();
                        // delegate to the ThingsAggregatorProxyActor which receives the results via a cluster stream:
                        aggregatorProxyActor.tell(retrieveThings, getSelf());
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
                .collect(Collectors.toList());

        if (!outOfSyncThingIds.isEmpty()) {
            final ThingsOutOfSync thingsOutOfSync =
                    ThingsOutOfSync.of(outOfSyncThingIds, queryThings.getDittoHeaders());
            pubSubMediator.tell(DistPubSubAccess.publishViaGroup(ThingsOutOfSync.TYPE, thingsOutOfSync),
                    ActorRef.noSender());
        }
    }

    private void stopMyself() {
        getContext().stop(getSelf());
    }

}
