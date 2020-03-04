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

import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.services.utils.aggregator.ThingsAggregatorProxyActor;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.search.SubscriptionManager;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommand;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CancelSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CreateSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.RequestSubscription;

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
    private final ActorMaterializer materializer;

    protected AbstractThingProxyActor(final ActorRef pubSubMediator,
            final ActorRef devOpsCommandsActor,
            final ActorRef conciergeForwarder) {

        super(pubSubMediator);

        this.devOpsCommandsActor = devOpsCommandsActor;
        this.conciergeForwarder = conciergeForwarder;
        this.materializer = ActorMaterializer.create(getContext());

        aggregatorProxyActor = getContext().actorOf(ThingsAggregatorProxyActor.props(conciergeForwarder),
                ThingsAggregatorProxyActor.ACTOR_NAME);

        subscriptionManager = getContext().actorOf(SubscriptionManager.props(Duration.ofMinutes(1L), pubSubMediator, conciergeForwarder,
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
                .match(CreateSubscription.class, cs -> subscriptionManager.forward(cs, getContext()))
                .match(RequestSubscription.class, rs -> subscriptionManager.forward(rs, getContext()))
                .match(CancelSubscription.class, cs -> subscriptionManager.forward(cs, getContext()))
                /* handle RetrieveThings in a special way */
                .match(RetrieveThings.class, rt -> aggregatorProxyActor.forward(rt, getContext()))
                .match(SudoRetrieveThings.class, srt -> aggregatorProxyActor.forward(srt, getContext()))

                .match(QueryThings.class, qt -> {
                    final ActorRef responseActor = getContext().actorOf(
                            QueryThingsPerRequestActor.props(qt, aggregatorProxyActor, getSender()));
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
