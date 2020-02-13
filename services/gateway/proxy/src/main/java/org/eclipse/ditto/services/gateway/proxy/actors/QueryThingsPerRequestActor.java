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
package org.eclipse.ditto.services.gateway.proxy.actors;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.thingsearch.SearchModelFactory;
import org.eclipse.ditto.model.thingsearch.SearchResult;
import org.eclipse.ditto.services.gateway.endpoints.config.GatewayHttpConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.HttpConfig;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThingsResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.event.DiagnosticLoggingAdapter;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.SourceRef;
import akka.stream.javadsl.StreamRefs;

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
    private final ActorRef conciergeForwarder;
    private final ActorRef aggregatorProxyActor;
    private final ActorRef originatingSender;
    private final ActorMaterializer materializer;

    private QueryThingsResponse queryThingsResponse;

    @SuppressWarnings("unused")
    private QueryThingsPerRequestActor(final QueryThings queryThings,
            final ActorRef conciergeForwarder,
            final ActorRef aggregatorProxyActor,
            final ActorRef originatingSender,
            final ActorMaterializer materializer) {

        this.queryThings = queryThings;
        this.conciergeForwarder = conciergeForwarder;
        this.aggregatorProxyActor = aggregatorProxyActor;
        this.originatingSender = originatingSender;
        this.materializer = materializer;
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
            final ActorRef conciergeForwarder,
            final ActorRef aggregatorProxyActor,
            final ActorRef originatingSender,
            final ActorMaterializer materializer) {

        return Props.create(QueryThingsPerRequestActor.class, queryThings, conciergeForwarder, aggregatorProxyActor,
                originatingSender, materializer);
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

                    final List<ThingId> thingIds = qtr.getSearchResult().stream()
                            .flatMap(val -> val.asObject().getValue(Thing.JsonFields.ID).stream().map(ThingId::of))
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
                        final SearchResult resultWithRetrievedItems = SearchModelFactory.newSearchResultBuilder()
                                .addAll(rtr.getEntity(rtr.getImplementedSchemaVersion()).asArray())
                                .nextPageOffset(queryThingsResponse.getSearchResult().getNextPageOffset().orElse(null))
                                .cursor(queryThingsResponse.getSearchResult().getCursor().orElse(null))
                                .build();
                        final QueryThingsResponse theQueryThingsResponse =
                                QueryThingsResponse.of(resultWithRetrievedItems, rtr.getDittoHeaders());
                        originatingSender.tell(theQueryThingsResponse, getSelf());
                    } else {
                        log.warning("Did not receive a QueryThingsResponse when a RetrieveThingsResponse occurred: {}",
                                rtr);
                    }

                    stopMyself();
                })
                .match(SourceRef.class, this::forwardSourceRef)
                .matchAny(any -> {
                    // all other messages (e.g. DittoRuntimeExceptions) are directly returned to the sender:
                    originatingSender.tell(any, getSender());
                    stopMyself();
                })
                .build();
    }

    private void forwardSourceRef(final SourceRef<?> sourceRef) {
        // TODO: move to aggregator proxy for parallelism and timeout configuration?
        final CompletionStage<SourceRef<Object>> sourceRefFuture = sourceRef.getSource()
                .mapAsync(1, element -> {
                    final ThingId id = ThingId.of(element.toString());
                    // TODO: smart handling for selecting thing ID only
                    final RetrieveThing retrieveThing = RetrieveThing.getBuilder(id, queryThings.getDittoHeaders())
                            .withSelectedFields(queryThings.getFields().orElse(null))
                            .build();
                    // TODO: replace literal timeout
                    return Patterns.ask(conciergeForwarder, retrieveThing, Duration.ofSeconds(10L));
                })
                .runWith(StreamRefs.sourceRef(), materializer);
        Patterns.pipe(sourceRefFuture, getContext().dispatcher()).to(originatingSender);
        stopMyself();
    }

    private void stopMyself() {
        getContext().stop(getSelf());
    }

}
