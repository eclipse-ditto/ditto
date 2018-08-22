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

import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.services.utils.aggregator.ThingsAggregatorProxyActor;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommand;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;

import akka.actor.ActorRef;
import akka.japi.pf.ReceiveBuilder;

/**
 * Abstract implementation of {@link AbstractProxyActor} for all {@link org.eclipse.ditto.signals.commands.base.Command}s
 * related to {@link org.eclipse.ditto.model.things.Thing}s.
 */
public abstract class AbstractThingProxyActor extends AbstractProxyActor {

    private final ActorRef devOpsCommandsActor;
    private final ActorRef conciergeForwarder;
    private final ActorRef aggregatorProxyActor;

    protected AbstractThingProxyActor(final ActorRef pubSubMediator,
            final ActorRef devOpsCommandsActor,
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
                    LogUtil.enhanceLogWithCorrelationId(getLogger(), command);
                    getLogger().debug("Got 'DevOpsCommand' message <{}>, forwarding to local devOpsCommandsActor",
                            command.getType());
                    devOpsCommandsActor.forward(command, getContext());
                })

                /* handle RetrieveThings in a special way */
                .match(RetrieveThings.class, rt -> aggregatorProxyActor.forward(rt, getContext()))
                .match(SudoRetrieveThings.class, srt -> aggregatorProxyActor.forward(srt, getContext()))

                .match(QueryThings.class, qt -> {
                            final ActorRef responseActor = getContext()
                                    .actorOf(QueryThingsPerRequestActor.props(qt, aggregatorProxyActor, getSender()));
                            conciergeForwarder.tell(qt, responseActor);
                        }
                )

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

}
