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

import org.eclipse.ditto.services.gateway.util.config.DittoGatewayConfig;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.services.utils.aggregator.ThingsAggregatorProxyActor;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.search.SubscriptionManager;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommand;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;

import akka.actor.ActorRef;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;

/**
 * Abstract implementation of {@link AbstractProxyActor} for all {@link org.eclipse.ditto.signals.commands.base.Command}s
 * related to {@link org.eclipse.ditto.model.things.Thing}s.
 */
public abstract class AbstractThingProxyActor extends AbstractProxyActor {

    private final ActorRef devOpsCommandsActor;
    private final ActorRef conciergeForwarder;
    private final ActorRef aggregatorProxyActor;
    private final ActorRef subscriptionManager;

    protected AbstractThingProxyActor(final ActorRef pubSubMediator,
            final ActorRef devOpsCommandsActor,
            final ActorRef conciergeForwarder) {

        super(pubSubMediator);

        this.devOpsCommandsActor = devOpsCommandsActor;
        this.conciergeForwarder = conciergeForwarder;
        final ActorMaterializer materializer = ActorMaterializer.create(getContext());

        aggregatorProxyActor = getContext().actorOf(ThingsAggregatorProxyActor.props(conciergeForwarder),
                ThingsAggregatorProxyActor.ACTOR_NAME);

        final DittoGatewayConfig gatewayConfig =
                DittoGatewayConfig.of(DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config()));

        subscriptionManager =
                getContext().actorOf(
                        SubscriptionManager.props(gatewayConfig.getHttpConfig().getRequestTimeout(), pubSubMediator, conciergeForwarder,
                                materializer),
                        SubscriptionManager.ACTOR_NAME);
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

                /* handle ThingSearch in a special way */
                .match(ThingSearchCommand.class, cs -> subscriptionManager.forward(cs, getContext()))
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
