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

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.services.models.concierge.ConciergeEnvelope;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingsResponse;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.ThingSearchSudoCommand;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommand;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;

import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;
import akka.routing.FromConfig;

/**
 * Abstract implementation of {@link AbstractProxyActor} for all {@link org.eclipse.ditto.signals.commands.base.Command}s
 * related to {@link org.eclipse.ditto.model.things.Thing}s.
 */
public abstract class AbstractThingProxyActor extends AbstractProxyActor {

    private static final String THINGS_SEARCH_ACTOR_PATH = "/user/thingsSearchRoot/thingsSearch";

    private final ActorRef pubSubMediator;
    private final ActorRef devOpsCommandsActor;
    private final ActorRef thingsAggregator;
    private final ConciergeEnvelope conciergeEnvelope;

    protected AbstractThingProxyActor(final ActorRef pubSubMediator,
            final ActorRef devOpsCommandsActor,
            final ActorRef conciergeShardRegion) {
        super(pubSubMediator);

        this.pubSubMediator = pubSubMediator;
        this.devOpsCommandsActor = devOpsCommandsActor;

        conciergeEnvelope = new ConciergeEnvelope(pubSubMediator, conciergeShardRegion);

        thingsAggregator = getContext().actorOf(FromConfig.getInstance().props(
                ThingsAggregatorActor.props(getContext().self())), ThingsAggregatorActor.ACTOR_NAME);
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

                /* Sudo Commands */
                .match(SudoRetrieveThings.class, command -> {
                    getLogger().debug("Got 'SudoRetrieveThings' message, forwarding to the Things Aggregator");
                    if (command.getThingIds().isEmpty()) {
                        getLogger().debug("Got 'SudoRetrieveThings' message with no ThingIds");
                        notifySender(SudoRetrieveThingsResponse.of(JsonArray.newBuilder().build(),
                                command.getDittoHeaders()));
                    } else {
                        getLogger().debug("Got 'SudoRetrieveThings' message, forwarding to the Things Aggregator");
                        thingsAggregator.forward(command, getContext());
                    }
                })

                // TODO CR-5434
                /* Thing Commands */
                .match(RetrieveThings.class, command -> {
                    if (command.getThingIds().isEmpty()) {
                        getLogger().debug("Got 'RetrieveThings' message with no ThingIds");
                        notifySender(RetrieveThingsResponse.of(JsonFactory.newArray(),
                                command.getNamespace().orElse(null), command.getDittoHeaders()));
                    } else {
                        getLogger().debug("Got 'RetrieveThings' message, forwarding to the Things Aggregator");
                        thingsAggregator.forward(command, getContext());
                    }
                })

                /* Search Commands */
                .match(ThingSearchCommand.class, command -> pubSubMediator.tell(
                        new DistributedPubSubMediator.Send(THINGS_SEARCH_ACTOR_PATH, command), getSender()))

                .match(ThingSearchSudoCommand.class, command -> pubSubMediator.tell(
                        new DistributedPubSubMediator.Send(THINGS_SEARCH_ACTOR_PATH, command), getSender()))

                /* send all other Commands to Concierge Service */
                .match(Command.class, this::forwardToConciergeService)

                /* Live Signals */
                .match(Signal.class, ProxyActor::isLiveSignal, this::forwardToConciergeService)
        ;
    }

    @Override
    protected void addResponseBehaviour(final ReceiveBuilder receiveBuilder) {

    }

    @Override
    protected void addErrorBehaviour(final ReceiveBuilder receiveBuilder) {
        // do nothing
    }

    protected void forwardToConciergeService(final Signal<?> signal) {
        conciergeEnvelope.dispatch(signal, getSender());
    }

}
