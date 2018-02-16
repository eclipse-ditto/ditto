/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.amqpbridge.messaging;

import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.model.amqpbridge.ConnectionStatus;
import org.eclipse.ditto.model.amqpbridge.MappingContext;
import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.amqpbridge.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnectionResponse;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnectionStatusResponse;

import com.typesafe.config.Config;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.cluster.pubsub.DistributedPubSub;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.routing.DefaultResizer;
import akka.routing.RoundRobinPool;
import scala.concurrent.duration.Duration;

/**
 * Base class for *ClientActors which implement the connection handling for AMQP 0.9.1 or 1.0.
 */
public abstract class BaseClientActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    protected final ActorRef pubSubMediator;
    private final java.time.Duration initTimeout;
    private final String connectionId;
    private final ActorRef connectionActor;

    protected ActorRef commandProcessor;
    protected AmqpConnection amqpConnection;
    private ConnectionStatus connectionStatus;
    protected List<MappingContext> mappingContexts;
    protected final Receive initHandling;

    protected BaseClientActor(final String connectionId, final ActorRef connectionActor) {
        this.connectionId = connectionId;
        this.connectionActor = connectionActor;
        this.pubSubMediator = DistributedPubSub.get(getContext().getSystem()).mediator();
        final Config config = getContext().getSystem().settings().config();
        initTimeout = config.getDuration(ConfigKeys.Client.INIT_TIMEOUT);

        initHandling = ReceiveBuilder.create()
                .match(ReceiveTimeout.class, rt -> handleReceiveTimeout())
                .match(RetrieveConnectionResponse.class, this::handleConnectionResponse)
                .match(RetrieveConnectionStatusResponse.class, this::handleStatusResponse)
                .build();
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        // we expect a connect message within timeout, otherwise we ask ConnectionActor to send us connection data
        getContext().setReceiveTimeout(Duration.fromNanos(initTimeout.toNanos()));
    }

    protected void startCommandProcessor() {
        ConditionChecker.checkNotNull(amqpConnection, "AmqpConnection");
        ConditionChecker.checkNotNull(mappingContexts, "MappingContexts");
        if (commandProcessor == null) {

            log.debug("Starting CommandProcessorActor with pool size of {}.", amqpConnection.getProcessorPoolSize());
            final Props commandProcessorProps =
                    CommandProcessorActor.props(pubSubMediator, amqpConnection.getAuthorizationSubject(),
                            mappingContexts);
            final String amqpCommandProcessorName = getCommandProcessorActorName(amqpConnection.getId());

            final DefaultResizer resizer = new DefaultResizer(1, amqpConnection.getProcessorPoolSize());
            commandProcessor = getContext().actorOf(new RoundRobinPool(1)
                    .withDispatcher("command-processor-dispatcher")
                    .withResizer(resizer)
                    .props(commandProcessorProps), amqpCommandProcessorName);
        }
    }

    protected void stopCommandProcessor() {
        if (commandProcessor != null) {
            log.debug("Stopping CommandProcessorActor.");
            context().stop(commandProcessor);
            commandProcessor = null;
        }
    }

    private String getCommandProcessorActorName(final String connectionId) {
        return escapeActorName(CommandProcessorActor.ACTOR_NAME_PREFIX + connectionId);
    }

    protected String escapeActorName(final String name) {
        return name.replace('/', '_');
    }

    protected ActorRef startChildActor(final String name, final Props props) {
        log.debug("Starting child actor '{}'", name);
        final String nameEscaped = escapeActorName(name);
        return getContext().actorOf(props, nameEscaped);
    }

    protected void stopChildActor(final String name) {
        final String nameEscaped = escapeActorName(name);
        final Optional<ActorRef> child = getContext().findChild(nameEscaped);
        if (child.isPresent()) {
            log.debug("Stopping child actor '{}'", nameEscaped);
            getContext().stop(child.get());
        } else {
            log.debug("Cannot stop child actor '{}' because it does not exist.");
        }
    }

    protected void stopChildActor(final ActorRef actor) {
        log.debug("Stopping child actor '{}'", actor.path());
        getContext().stop(actor);
    }

    protected void handleReceiveTimeout() {
        log.info(
                "Did not receive connect command within {}, requesting information from connection actor for connection <{}>.",
                initTimeout, connectionId);
        connectionActor.tell(RetrieveConnection.of(connectionId, DittoHeaders.empty()), self());
        connectionActor.tell(RetrieveConnectionStatus.of(connectionId, DittoHeaders.empty()), self());
    }

    protected void handleStatusResponse(final RetrieveConnectionStatusResponse rcr) {
        this.connectionStatus = rcr.getConnectionStatus();
        log.debug("Received ConnectionStatus: {}", connectionStatus);
        connectIfStatusIsOpen();
    }

    protected void handleConnectionResponse(final RetrieveConnectionResponse rcr) {
        this.amqpConnection = rcr.getAmqpConnection();
        log.debug("Received AmqpConnection: {}", amqpConnection);
        connectIfStatusIsOpen();
    }

    private void connectIfStatusIsOpen() {
        if (amqpConnection != null && connectionStatus != null && ConnectionStatus.OPEN.equals(connectionStatus)) {
            final CreateConnection connect = CreateConnection.of(amqpConnection, DittoHeaders.empty());
            log.info("Sending CreateConnection to myself: {}", connect);
            self().tell(connect, sender());
        }
    }
}
