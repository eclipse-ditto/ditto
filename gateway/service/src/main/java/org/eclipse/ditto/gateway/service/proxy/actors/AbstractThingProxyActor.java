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

import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.internal.utils.aggregator.ThingsAggregatorProxyActor;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.api.devops.signals.commands.DevOpsCommand;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThings;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.japi.pf.ReceiveBuilder;

/**
 * Abstract implementation of {@link AbstractProxyActor} for all {@link org.eclipse.ditto.base.model.signals.commands.Command}s
 * related to {@link org.eclipse.ditto.things.model.Thing}s.
 */
public abstract class AbstractThingProxyActor extends AbstractProxyActor {

    private final ActorSelection devOpsCommandsActor;
    private final ActorRef conciergeForwarder;
    private final ActorRef aggregatorProxyActor;

    protected AbstractThingProxyActor(final ActorRef pubSubMediator,
            final ActorSelection devOpsCommandsActor,
            final ActorRef conciergeForwarder) {

        super(pubSubMediator);

        this.devOpsCommandsActor = devOpsCommandsActor;
        this.conciergeForwarder = conciergeForwarder;

        aggregatorProxyActor = getContext().actorOf(ThingsAggregatorProxyActor.props(conciergeForwarder),
                ThingsAggregatorProxyActor.ACTOR_NAME);
    }

    @Override
    protected void addCommandBehaviour(final ReceiveBuilder receiveBuilder) {
        receiveBuilder
                /* DevOps Commands */
                .match(DevOpsCommand.class, command -> {
                    getLogger().withCorrelationId(command)
                            .debug("Got 'DevOpsCommand' message <{}>, forwarding to local devOpsCommandsActor",
                                    command.getType());
                    devOpsCommandsActor.forward(command, getContext());
                })
                /* handle RetrieveThings in a special way */
                .match(RetrieveThings.class, rt -> aggregatorProxyActor.forward(rt, getContext()))
                .match(SudoRetrieveThings.class, srt -> aggregatorProxyActor.forward(srt, getContext()))

                .match(QueryThings.class, qt -> {
                    final ActorRef responseActor = getContext().actorOf(
                            QueryThingsPerRequestActor.props(qt, aggregatorProxyActor, getSender(),
                                    pubSubMediator));
                    conciergeForwarder.tell(qt, responseActor);
                })

                /* send all other Commands to Concierge Service */
                .match(Command.class, this::forwardToConciergeService)

                /* Live Signals */
                .match(Signal.class, AbstractProxyActor::isLiveCommandOrEvent, this::forwardToConciergeService);
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

}
