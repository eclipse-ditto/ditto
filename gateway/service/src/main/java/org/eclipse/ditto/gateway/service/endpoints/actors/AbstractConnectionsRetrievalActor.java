/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.gateway.service.endpoints.actors;

import java.time.Duration;

import org.eclipse.ditto.connectivity.model.ConnectivityInternalErrorException;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityErrorResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnections;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ReceiveTimeout;
import akka.japi.pf.ReceiveBuilder;

public abstract class AbstractConnectionsRetrievalActor extends AbstractActor {

    protected final ThreadSafeDittoLogger logger = DittoLoggerFactory.getThreadSafeLogger(getClass());
    protected final ActorRef edgeCommandForwarder;
    protected final ActorRef sender;
    protected final int connectionsRetrieveLimit;
    protected RetrieveConnections initialCommand;

    public AbstractConnectionsRetrievalActor(final ActorRef edgeCommandForwarder, final ActorRef sender, final Duration timeout) {
        this.edgeCommandForwarder = edgeCommandForwarder;
        this.sender = sender;
        this.connectionsRetrieveLimit =
                DittoGatewayConfig.of(DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config()))
                        .getCommandConfig()
                        .connectionsRetrieveLimit();
        getContext().setReceiveTimeout(timeout);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveConnections.class, this::handleRetrieveConnections)
                .match(ReceiveTimeout.class, timeout -> handleTimeout())
                .matchAny(msg -> logger.warn("Unknown message: <{}>", msg))
                .build();
    }

    protected abstract void retrieveConnections(final RetrieveConnections rc);
    protected abstract Receive commandResponseAwaitingBehaviour();

    private Receive responseAwaitingBehavior(){
        return commandResponseAwaitingBehaviour().orElse(ReceiveBuilder.create()
                .match(ReceiveTimeout.class, timeout -> handleTimeout())
                .matchAny(msg -> logger.warn("Unknown message: <{}>", msg))
                .build());
    }

    private void handleRetrieveConnections(RetrieveConnections retrieveConnections) {
        getContext().become(responseAwaitingBehavior());
        this.initialCommand = retrieveConnections;
        retrieveConnections(retrieveConnections);
    }



    protected void handleTimeout() {
        ConnectivityInternalErrorException.Builder builder = ConnectivityInternalErrorException.newBuilder();
        ConnectivityErrorResponse response;
        if (initialCommand != null) {
            builder.dittoHeaders(initialCommand.getDittoHeaders())
                    .message("RetrieveConnections command timed out.");
            response = ConnectivityErrorResponse.of(builder.build(), initialCommand.getDittoHeaders());
        } else {
            builder.message("Actor time out. command timed out.");
            response = ConnectivityErrorResponse.of(ConnectivityInternalErrorException.newBuilder().build());
        }
        sender.tell(response, getSelf());
        stop();
    }

    void stop() {
        getContext().stop(getSelf());
    }
}
