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

import org.eclipse.ditto.base.api.devops.signals.commands.DevOpsCommand;
import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveStatistics;
import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveStatisticsDetails;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnections;
import org.eclipse.ditto.gateway.service.endpoints.actors.ConnectionsRetrievalActorPropsFactory;
import org.eclipse.ditto.gateway.service.util.config.endpoints.HttpConfig;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThings;

import com.typesafe.config.Config;

import akka.actor.AbstractActor;
import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;

/**
 * A command proxy for the Ditto gateway.
 */
public final class GatewayProxyActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "proxy";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final ActorRef pubSubMediator;
    private final ActorSelection devOpsCommandsActor;
    private final ActorRef edgeCommandForwarder;
    private final HttpConfig httpConfig;
    private final ActorRef statisticsActor;
    private final ConnectionsRetrievalActorPropsFactory connectionsRetrievalActorPropsFactory;

    @SuppressWarnings("unused")
    private GatewayProxyActor(final ActorRef pubSubMediator, final ActorSelection devOpsCommandsActor,
            final ActorRef edgeCommandForwarder, final HttpConfig httpConfig) {
        this.pubSubMediator = pubSubMediator;
        this.devOpsCommandsActor = devOpsCommandsActor;
        this.edgeCommandForwarder = edgeCommandForwarder;
        this.httpConfig = httpConfig;
        this.statisticsActor = getContext().actorOf(StatisticsActor.props(pubSubMediator), StatisticsActor.ACTOR_NAME);

        final Config dittoExtensionConfig =
                ScopedConfig.dittoExtension(getContext().getSystem().settings().config());
        this.connectionsRetrievalActorPropsFactory =
                ConnectionsRetrievalActorPropsFactory.get(getContext().getSystem(), dittoExtensionConfig);
    }

    /**
     * Creates Akka configuration object Props for this GatewayProxyActor.
     *
     * @param pubSubMediator the Pub/Sub mediator to use for subscribing for events.
     * @param devOpsCommandsActor the Actor ref to the local DevOpsCommandsActor.
     * @param edgeCommandForwarder the Actor ref to the {@code EdgeCommandForwarderActor}.
     * @param httpConfig the http config.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator, final ActorSelection devOpsCommandsActor,
            final ActorRef edgeCommandForwarder, final HttpConfig httpConfig) {

        return Props.create(GatewayProxyActor.class, pubSubMediator, devOpsCommandsActor, edgeCommandForwarder,
                httpConfig);
    }

    static boolean isLiveCommandOrEvent(final Signal<?> signal) {
        return StreamingType.isLiveSignal(signal);
    }

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
                }).match(DevOpsCommand.class, command -> {
                    log.withCorrelationId(command)
                            .debug("Got 'DevOpsCommand' message <{}>, forwarding to local devOpsCommandsActor",
                                    command.getType());
                    devOpsCommandsActor.forward(command, getContext());
                })
                .match(QueryThings.class, qt -> {
                    final ActorRef responseActor = getContext().actorOf(
                            QueryThingsPerRequestActor.props(qt, edgeCommandForwarder, getSender(), pubSubMediator,
                                    httpConfig)
                    );
                    edgeCommandForwarder.tell(qt, responseActor);
                })
                .match(RetrieveConnections.class, rc -> {
                    final ActorRef connectionsRetrievalActor = getContext().actorOf(
                            connectionsRetrievalActorPropsFactory.getActorProps(edgeCommandForwarder, getSender())
                    );
                    connectionsRetrievalActor.tell(rc, getSender());
                })
                /* send all other Commands to command forwarder */
                .match(Command.class, this::forwardToCommandForwarder)

                /* Live Signals */
                .match(Signal.class, GatewayProxyActor::isLiveCommandOrEvent, this::forwardToCommandForwarder)
                .match(Status.Failure.class, failure -> {
                    Throwable cause = failure.cause();
                    if (cause instanceof JsonRuntimeException) {
                        cause = new DittoJsonException((RuntimeException) cause);
                    }
                    getSender().tell(cause, getSelf());
                })
                .match(DittoRuntimeException.class, cre -> getSender().tell(cre, getSelf()))
                .matchAny(m -> log.warning("Got unknown message, expected a 'Command': {}", m));

        return receiveBuilder.build();
    }

    private void forwardToCommandForwarder(final Signal<?> signal) {
        edgeCommandForwarder.forward(signal, getContext());
    }

}
