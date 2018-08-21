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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.thingsearch.SearchResult;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.services.utils.aggregator.ThingsAggregatorProxyActor;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommand;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThingsResponse;

import akka.actor.ActorRef;
import akka.japi.pf.ReceiveBuilder;

/**
 * Abstract implementation of {@link AbstractProxyActor} for all {@link org.eclipse.ditto.signals.commands.base.Command}s
 * related to {@link org.eclipse.ditto.model.things.Thing}s.
 */
public abstract class AbstractThingProxyActor extends AbstractProxyActor {

    private final ActorRef devOpsCommandsActor;
    private final ActorRef conciergeForwarder;
    private final ActorRef aggregatorProxy;
    private final Map<String, QueryThingsHolder> queryThingsRequests;

    protected AbstractThingProxyActor(final ActorRef pubSubMediator,
            final ActorRef devOpsCommandsActor,
            final ActorRef conciergeForwarder) {
        super(pubSubMediator);

        this.devOpsCommandsActor = devOpsCommandsActor;
        this.conciergeForwarder = conciergeForwarder;

        aggregatorProxy = getContext().actorOf(ThingsAggregatorProxyActor.props(conciergeForwarder),
                ThingsAggregatorProxyActor.ACTOR_NAME);
        queryThingsRequests = new HashMap<>();
    }

    @Override
    protected void addCommandBehaviour(final ReceiveBuilder receiveBuilder) {
        receiveBuilder
                /* DevOps Commands */
                .match(DevOpsCommand.class, command -> {
                    LogUtil.enhanceLogWithCorrelationId(getLogger(), command);
                    getLogger().debug("Got 'DevOpsCommand' message <{}>, forwarding to local devOpsCommandsActor",
                            command.getType());
                    devOpsCommandsActor.forward(command, getContext());
                })

                /* handle RetrieveThings in a special way */
                .match(RetrieveThings.class, rt -> aggregatorProxy.forward(rt, getContext()))
                .match(SudoRetrieveThings.class, srt -> aggregatorProxy.forward(srt, getContext()))

                .match(QueryThings.class, qt -> {
                    final String cId = qt.getDittoHeaders().getCorrelationId().orElse("");
                    queryThingsRequests.put(cId, new QueryThingsHolder(qt, getSender()));
                    conciergeForwarder.tell(qt, getSelf());
                })
                .match(QueryThingsResponse.class, qtr -> {
                    final QueryThingsHolder queryThingsHolder =
                            queryThingsRequests.get(qtr.getDittoHeaders().getCorrelationId().orElse(""));

                    queryThingsHolder.queryThingsResponse = qtr;

                    final List<String> thingIds = qtr.getSearchResult().stream()
                            .map(val -> val.asObject().getValue(Thing.JsonFields.ID).orElse(null))
                            .collect(Collectors.toList());

                    if (thingIds.isEmpty()) {
                        queryThingsHolder.originatingSender.tell(qtr, getSelf());
                    } else {
                        final RetrieveThings retrieveThings = RetrieveThings.getBuilder(thingIds)
                                .dittoHeaders(qtr.getDittoHeaders())
                                .selectedFields(queryThingsHolder.queryThings.getFields())
                                .build();
                        aggregatorProxy.tell(retrieveThings, getSelf());
                    }
                })
                .match(RetrieveThingsResponse.class, rtr -> {
                    final QueryThingsHolder queryThingsHolder =
                            queryThingsRequests.remove(rtr.getDittoHeaders().getCorrelationId().orElse(""));

                    final QueryThingsResponse queryThingsResponse = QueryThingsResponse.of(SearchResult.newBuilder()
                                    .addAll(rtr.getThings().stream().map(Thing::toJson).collect(JsonCollectors.valuesToArray()))
                                    .nextPageOffset(queryThingsHolder.queryThingsResponse.getSearchResult().getNextPageOffset())
                                    .build(),
                            rtr.getDittoHeaders()
                    );

                    queryThingsHolder.originatingSender.tell(queryThingsResponse, getSelf());
                })

                /* send all other Commands to Concierge Service */
                .match(Command.class, this::forwardToConciergeService)

                /* Live Signals */
                .match(Signal.class, ProxyActor::isLiveSignal, this::forwardToConciergeService);
    }

    @Override
    protected void addResponseBehaviour(final ReceiveBuilder receiveBuilder) {
        // do nothing
    }

    @Override
    protected void addErrorBehaviour(final ReceiveBuilder receiveBuilder) {
        // do nothing
    }

    private void forwardToConciergeService(final Signal<?> signal) {
        conciergeForwarder.forward(signal, getContext());
    }

    private static final class QueryThingsHolder {
        private final QueryThings queryThings;
        private final ActorRef originatingSender;
        private QueryThingsResponse queryThingsResponse;

        private QueryThingsHolder(final QueryThings queryThings, final ActorRef originatingSender) {
            this.queryThings = queryThings;
            this.originatingSender = originatingSender;
        }
    }

}
