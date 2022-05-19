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

import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveStatistics;
import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveStatisticsDetails;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.json.JsonRuntimeException;

import akka.actor.AbstractActor;
import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.cluster.pubsub.DistributedPubSubMediator;
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

    /**
     * Akka pub-sub mediator.
     */
    protected final ActorRef pubSubMediator;

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final ActorRef statisticsActor;

    AbstractProxyActor(final ActorRef pubSubMediator) {
        this.pubSubMediator = pubSubMediator;
        statisticsActor = getContext().actorOf(StatisticsActor.props(pubSubMediator), StatisticsActor.ACTOR_NAME);
    }

    static boolean isLiveCommandOrEvent(final Signal<?> signal) {
        return StreamingType.isLiveSignal(signal);
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
                .matchAny(e -> (SupervisorStrategy.Directive) SupervisorStrategy.escalate())
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
                })
                .match(RetrieveStatisticsDetails.class, retrieveStatisticsDetails -> {
                    log.debug("Got 'RetrieveStatisticsDetails' message");
                    statisticsActor.forward(retrieveStatisticsDetails, getContext());
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

    protected DittoDiagnosticLoggingAdapter getLogger() {
        return log;
    }


}
