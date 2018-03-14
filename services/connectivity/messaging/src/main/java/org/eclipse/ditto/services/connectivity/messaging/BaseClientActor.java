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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.connectivity.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatusResponse;

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
 * Base class for ClientActors which implement the connection handling for AMQP 0.9.1 or 1.0.
 *
 * The actor expects to receive a {@link CreateConnection} command after it was started. If this command is not received
 * within timeout (can be the case when this actor is remotely deployed after the command was sent) the actor requests
 * the required information from ConnectionActor.
 */
public abstract class BaseClientActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    protected final ActorRef pubSubMediator;
    protected final Receive initHandling;
    protected final String connectionId;
    protected final String pubSubTargetPath;

    private final ActorRef connectionActor;
    private final java.time.Duration initTimeout;

    @Nullable protected Connection connection;
    @Nullable protected List<MappingContext> mappingContexts;
    @Nullable protected ActorRef messageMappingProcessor;
    @Nullable private ConnectionStatus connectionStatus;

    protected BaseClientActor(final String connectionId, final ActorRef connectionActor,
            final String pubSubTargetPath) {
        this.connectionId = checkNotNull(connectionId, "connectionId");
        this.connectionActor = checkNotNull(connectionActor, "connectionActor");
        this.pubSubTargetPath = pubSubTargetPath;
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

    protected void startMessageMappingProcessor(final ActorRef commandProducer) {
        checkNotNull(connection, "Connection");
        checkNotNull(mappingContexts, "MappingContexts");
        checkNotNull(pubSubTargetPath, "PubSubTargetPath");
        if (messageMappingProcessor == null) {

            log.debug("Starting MessageMappingProcessorActor with pool size of {}.", connection.getProcessorPoolSize());
            final Props props = MessageMappingProcessorActor.props(pubSubMediator, pubSubTargetPath, commandProducer,
                            connection.getAuthorizationContext(), mappingContexts);
            final String messageMappingProcessorName = getMessageMappingProcessorActorName(connection.getId());

            final DefaultResizer resizer = new DefaultResizer(1, connection.getProcessorPoolSize());
            messageMappingProcessor = getContext().actorOf(new RoundRobinPool(1)
                    .withDispatcher("message-mapping-processor-dispatcher")
                    .withResizer(resizer)
                    .props(props), messageMappingProcessorName);
        }
    }

    protected void stopMessageMappingProcessor() {
        if (messageMappingProcessor != null) {
            log.debug("Stopping MessageMappingProcessorActor.");
            context().stop(messageMappingProcessor);
            messageMappingProcessor = null;
        }
    }

    private String getMessageMappingProcessorActorName(final String connectionId) {
        return escapeActorName(MessageMappingProcessorActor.ACTOR_NAME_PREFIX + connectionId);
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

    private void handleReceiveTimeout() {
        log.info(
                "Did not receive connect command within {}, requesting information from connection actor for connection <{}>.",
                initTimeout, connectionId);
        connectionActor.tell(RetrieveConnection.of(connectionId, DittoHeaders.empty()), self());
        connectionActor.tell(RetrieveConnectionStatus.of(connectionId, DittoHeaders.empty()), self());
    }

    private void handleStatusResponse(final RetrieveConnectionStatusResponse rcr) {
        if (connectionStatus == null) {
            connectionStatus = rcr.getConnectionStatus();
            log.debug("Received ConnectionStatus: {}", connectionStatus);
            connectIfStatusIsOpen();
        }
    }

    private void handleConnectionResponse(final RetrieveConnectionResponse rcr) {
        if (connection == null) {
            connection = rcr.getConnection();
            log.debug("Received Connection: {}", connection);
            connectIfStatusIsOpen();
        }
    }

    private void connectIfStatusIsOpen() {
        if (connection != null && connectionStatus != null && ConnectionStatus.OPEN.equals(connectionStatus)) {
            final CreateConnection connect = CreateConnection.of(connection, DittoHeaders.empty());
            log.info("Sending CreateConnection to myself: {}", connect);
            self().tell(connect, sender());
        }
    }

    protected boolean isConsumingCommands() {
        return connection != null && connection.getSources().isPresent() &&
                !connection.getSources().get().isEmpty();
    }

    protected boolean isPublishingEvents() {
        return connection != null && connection.getEventTarget().isPresent() &&
                !connection.getEventTarget().get().isEmpty();
    }

    /**
     * @return the sources configured for this connection or an empty set if no sources were configured.
     */
    protected Set<String> getSourcesOrEmptySet() {
        return connection != null ? connection.getSources().orElse(Collections.emptySet()) :
                Collections.emptySet();
    }
}
