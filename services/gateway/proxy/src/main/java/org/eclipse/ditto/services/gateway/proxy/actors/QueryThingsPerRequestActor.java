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
package org.eclipse.ditto.services.gateway.proxy.actors;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.thingsearch.SearchResult;
import org.eclipse.ditto.services.gateway.starter.service.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThingsResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import scala.concurrent.duration.Duration;

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

        final Duration timeout = Duration.create(getContext().system().settings().config()
                        .getDuration(ConfigKeys.AKKA_HTTP_SERVER_REQUEST_TIMEOUT).getSeconds(),
                TimeUnit.SECONDS);
        getContext().setReceiveTimeout(timeout);
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
                .match(ReceiveTimeout.class, receiveTimeout -> {
                    log.debug("Got ReceiveTimeout");
                    stopMyself();
                })
                .match(QueryThingsResponse.class, qtr -> {
                    LogUtil.enhanceLogWithCorrelationId(log, qtr);
                    queryThingsResponse = qtr;

                    log.debug("Received QueryThingsResponse: {}", qtr);

                    final List<String> thingIds = qtr.getSearchResult().stream()
                            .map(val -> val.asObject().getValue(Thing.JsonFields.ID).orElse(null))
                            .collect(Collectors.toList());

                    if (thingIds.isEmpty()) {
                        // shortcut - for no search results we don't have to lookup the things
                        originatingSender.tell(qtr, getSelf());

                        stopMyself();
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
                    LogUtil.enhanceLogWithCorrelationId(log, rtr);
                    log.debug("Received RetrieveThingsResponse: {}", rtr);

                    if (queryThingsResponse != null) {
                        final QueryThingsResponse theQueryThingsResponse =
                                QueryThingsResponse.of(SearchResult.newBuilder()
                                                .addAll(rtr.getEntity(rtr.getImplementedSchemaVersion()).asArray())
                                                .nextPageOffset(queryThingsResponse.getSearchResult().getNextPageOffset())
                                                .nextPageKey(
                                                        queryThingsResponse.getSearchResult().getNextPageKey().orElse(null))
                                                .build(),
                                        rtr.getDittoHeaders()
                                );
                        originatingSender.tell(theQueryThingsResponse, getSelf());
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

    private void stopMyself() {
        getContext().stop(getSelf());
    }


}
