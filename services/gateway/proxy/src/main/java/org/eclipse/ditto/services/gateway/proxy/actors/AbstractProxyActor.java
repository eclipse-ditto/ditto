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

import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatistics;

import akka.actor.AbstractActor;
import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;

/**
 * Abstract base implementation for a command proxy.
 */
public abstract class AbstractProxyActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "proxy";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef statisticsActor;

    AbstractProxyActor(final ActorRef pubSubMediator) {
        statisticsActor = getContext().actorOf(StatisticsActor.props(pubSubMediator), StatisticsActor.ACTOR_NAME);
    }

    static boolean isLiveSignal(final Signal<?> signal) {
        return signal.getDittoHeaders().getChannel().filter(TopicPath.Channel.LIVE.getName()::equals).isPresent();
    }

    protected abstract void addCommandBehaviour(final ReceiveBuilder receiveBuilder);

    protected abstract void addResponseBehaviour(final ReceiveBuilder receiveBuilder);

    protected abstract void addErrorBehaviour(final ReceiveBuilder receiveBuilder);


    @Override
    public SupervisorStrategy supervisorStrategy() {
        return new OneForOneStrategy(true, DeciderBuilder
                .match(NullPointerException.class, e -> {
                    log.error(e, "NullPointer in child actor - restarting it...", e.getMessage());
                    log.info("Restarting child...");
                    return SupervisorStrategy.restart();
                })
                .match(ActorKilledException.class, e -> {
                    log.error(e.getCause(), "ActorKilledException in child actor - stopping it...");
                    return SupervisorStrategy.stop();
                })
                .matchAny(e -> SupervisorStrategy.escalate())
                .build());
    }

    @Override
    public Receive createReceive() {
        final ReceiveBuilder receiveBuilder = ReceiveBuilder.create();

        // common commands
        receiveBuilder
                .match(RetrieveStatistics.class, retrieveStatistics -> {
                    log.debug("Got 'RetrieveStatistics' message");
                    statisticsActor.forward(retrieveStatistics, getContext());
                });

        // specific commands
        addCommandBehaviour(receiveBuilder);

        // specific responses
        addResponseBehaviour(receiveBuilder);

        // specific errors
        addErrorBehaviour(receiveBuilder);

        // common errors
        receiveBuilder
                .match(Status.Failure.class, failure -> {
                    Throwable cause = failure.cause();
                    if (cause instanceof JsonRuntimeException) {
                        cause = new DittoJsonException((RuntimeException) cause);
                    }
                    getSender().tell(cause, getSelf());
                })
                .match(DittoRuntimeException.class, cre -> getSender().tell(cre, getSelf()))
                .match(DistributedPubSubMediator.SubscribeAck.class, subscribeAck ->
                        getLogger().debug("Successfully subscribed to distributed pub/sub on topic '{}'",
                                subscribeAck.subscribe().topic())
                )
                .matchAny(m -> getLogger().warning("Got unknown message, expected a 'Command': {}", m));

        return receiveBuilder.build();
    }

    protected DiagnosticLoggingAdapter getLogger() {
        return log;
    }


}
