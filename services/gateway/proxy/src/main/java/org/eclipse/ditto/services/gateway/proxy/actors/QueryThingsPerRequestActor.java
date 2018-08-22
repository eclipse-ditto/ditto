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

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.thingsearch.SearchResult;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThingsResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;

/**
 *
 */
final class QueryThingsPerRequestActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final QueryThings queryThings;
    private final ActorRef aggregatorProxyActor;
    private final ActorRef originatingSender;

    private QueryThingsResponse queryThingsResponse;

    private QueryThingsPerRequestActor(final QueryThings queryThings, final ActorRef aggregatorProxyActor,
            final ActorRef originatingSender) {
        this.queryThings = queryThings;
        this.aggregatorProxyActor = aggregatorProxyActor;
        this.originatingSender = originatingSender;
        queryThingsResponse = null;
    }

    /**
     * Creates Akka configuration object Props for this QueryThingsPerRequestActor.
     *
     * @return the Akka configuration Props object
     */
    static Props props(final QueryThings queryThings, final ActorRef aggregatorProxyActor,
            final ActorRef originatingSender) {
        return Props.create(QueryThingsPerRequestActor.class, new Creator<QueryThingsPerRequestActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public QueryThingsPerRequestActor create() {
                return new QueryThingsPerRequestActor(queryThings, aggregatorProxyActor, originatingSender);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(QueryThingsResponse.class, qtr -> {
                    queryThingsResponse = qtr;

                    log.debug("Received QueryThingsResponse: {}", qtr);

                    final List<String> thingIds = qtr.getSearchResult().stream()
                            .map(val -> val.asObject().getValue(Thing.JsonFields.ID).orElse(null))
                            .collect(Collectors.toList());

                    if (thingIds.isEmpty()) {
                        // shortcut - for no search results we don't have to lookup the things
                        originatingSender.tell(qtr, getSelf());
                    } else {
                        final RetrieveThings retrieveThings = RetrieveThings.getBuilder(thingIds)
                                .dittoHeaders(qtr.getDittoHeaders())
                                .selectedFields(queryThings.getFields())
                                .build();
                        // delegate to the ThingsAggregatorProxyActor which receives the results via a cluster stream:
                        aggregatorProxyActor.tell(retrieveThings, getSelf());
                    }
                })
                .match(RetrieveThingsResponse.class, rtr -> {
                    log.debug("Received RetrieveThingsResponse: {}", rtr);

                    if (queryThingsResponse != null) {
                        final QueryThingsResponse theQueryThingsResponse =
                                QueryThingsResponse.of(SearchResult.newBuilder()
                                                .addAll(rtr.getThings()
                                                        .stream()
                                                        .map(t -> queryThings.getFields()
                                                                .map(fields -> t.toJson(rtr.getImplementedSchemaVersion(), fields))
                                                                .orElseGet(() -> t.toJson(rtr.getImplementedSchemaVersion()))
                                                        )
                                                        .collect(JsonCollectors.valuesToArray()))
                                                .nextPageOffset(queryThingsResponse.getSearchResult().getNextPageOffset())
                                                .build(),
                                        rtr.getDittoHeaders()
                                );
                        originatingSender.tell(theQueryThingsResponse, getSelf());
                    } else {
                        log.warning("Did not receive a QueryThingsResponse when a RetrieveThingsResponse occurred: {}",
                                rtr);
                    }

                    getContext().stop(getSelf());
                })
                .matchAny(any -> {
                    // all other messages (e.g. DittoRuntimeExceptions) are directly returned to the sender:
                    originatingSender.tell(any, getSender());
                    getContext().stop(getSelf());
                })
                .build();
    }


}
