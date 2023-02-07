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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.ErrorResponse;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityInternalErrorException;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityErrorResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionsAmountIllegalException;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveAllConnectionIds;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveAllConnectionIdsResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnections;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionsResponse;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.CommandConfig;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFieldSelector;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ReceiveTimeout;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;


/**
 * Abstract actor for retrieving multiple connections.
 * Implementers should add the custom retrieval logic.
 */

public abstract class AbstractConnectionsRetrievalActor extends AbstractActor {

    protected final ThreadSafeDittoLogger logger = DittoLoggerFactory.getThreadSafeLogger(getClass());
    protected final ActorRef edgeCommandForwarder;
    protected final ActorRef sender;
    protected final int connectionsRetrieveLimit;
    protected final Duration defaultTimeout;
    protected RetrieveConnections initialCommand;

    protected AbstractConnectionsRetrievalActor(final ActorRef edgeCommandForwarder, final ActorRef sender) {
        this.edgeCommandForwarder = edgeCommandForwarder;
        this.sender = sender;
        CommandConfig commandConfig =
                DittoGatewayConfig.of(DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config()))
                        .getCommandConfig();
        this.connectionsRetrieveLimit = commandConfig.connectionsRetrieveLimit();
        this.defaultTimeout = commandConfig.getDefaultTimeout();
        getContext().setReceiveTimeout(defaultTimeout);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveConnections.class, this::handleRetrieveConnections)
                .match(ReceiveTimeout.class, timeout -> handleTimeout())
                .matchAny(msg -> logger.warn("Unknown message: <{}>", msg))
                .build();
    }

    protected abstract void retrieveConnections(final RetrieveConnections retrieveConnections);

    protected void retrieveAllConnectionsIds(final RetrieveConnections retrieveConnections) {
        this.edgeCommandForwarder.tell(RetrieveAllConnectionIds.of(retrieveConnections.getDittoHeaders()), getSelf());
    }

    protected void retrieveConnectionsById(final RetrieveAllConnectionIdsResponse allConnectionIdsResponse) {
        final var connectionIds = allConnectionIdsResponse.getAllConnectionIds();
        if (initialCommand.getIdsOnly()) {
            final RetrieveConnectionsResponse response = RetrieveConnectionsResponse
                    .of(JsonArray.of(connectionIds), initialCommand.getDittoHeaders());
            sender.tell(response, getSelf());
            stop();
        } else {
            if (connectionIds.size() > connectionsRetrieveLimit) {
                sender.tell(ConnectionsAmountIllegalException.newBuilder(connectionsRetrieveLimit)
                        .dittoHeaders(initialCommand.getDittoHeaders())
                        .build(), getSelf());
                stop();
            } else {
                retrieveConnections(connectionIds
                        .stream()
                        .map(ConnectionId::of)
                        .toList(),
                        initialCommand.getSelectedFields().orElse(null),
                        initialCommand.getDittoHeaders()
                )
                        .thenAccept(retrieveConnectionsResponse -> {
                            sender.tell(retrieveConnectionsResponse, getSelf());
                            stop();
                        })
                        .exceptionally(t -> {
                            final ConnectivityInternalErrorException exception =
                                    ConnectivityInternalErrorException.newBuilder()
                                            .dittoHeaders(initialCommand.getDittoHeaders())
                                            .cause(t)
                                            .build();
                            final ConnectivityErrorResponse response =
                                    ConnectivityErrorResponse.of(exception, initialCommand.getDittoHeaders());
                            sender.tell(response, getSelf());
                            stop();
                            return null;
                        });
            }
        }
    }

    private Receive responseAwaitingBehavior(){
        return ReceiveBuilder.create()
                .match(RetrieveAllConnectionIdsResponse.class, this::retrieveConnectionsById)
                .match(ReceiveTimeout.class, timeout -> handleTimeout())
                .matchAny(msg -> logger.warn("Unknown message: <{}>", msg))
                .build();
    }

    private void handleRetrieveConnections(final RetrieveConnections retrieveConnections) {
        getContext().become(responseAwaitingBehavior());
        this.initialCommand = retrieveConnections;
        retrieveConnections(retrieveConnections);
    }

    private CompletionStage<RetrieveConnectionsResponse> retrieveConnections(
            final Collection<ConnectionId> connectionIds,
            @Nullable final JsonFieldSelector selectedFields,
            final DittoHeaders dittoHeaders) {

        checkNotNull(connectionIds, "connectionIds");
        checkNotNull(dittoHeaders, "dittoHeaders");

        final List<CompletableFuture<RetrieveConnectionResponse>> completableFutures = connectionIds.parallelStream()
                .map(connectionId -> retrieveConnection(RetrieveConnection.of(connectionId, selectedFields, dittoHeaders)))
                .map(CompletionStage::toCompletableFuture)
                .toList();

        final List<ConnectionId> connectionIdList = new ArrayList<>(connectionIds);

        // this comparator is used to support returning the connections in the same order they were requested
        final Comparator<RetrieveConnectionResponse> sorter = (r1, r2) -> {
            final ConnectionId r1ConnectionId = r1.getEntityId();
            final ConnectionId r2ConnectionId = r2.getEntityId();
            return Integer.compare(connectionIdList.indexOf(r1ConnectionId), connectionIdList.indexOf(r2ConnectionId));
        };

        return CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> completableFutures.stream()
                        .map(CompletableFuture::join)
                        .toList())
                .thenApply(retrieveConnectionResponses -> {
                    final JsonArray connections = retrieveConnectionResponses.stream()
                            .sorted(sorter)
                            .map(RetrieveConnectionResponse::getEntity)
                            .collect(JsonCollectors.valuesToArray());

                    return RetrieveConnectionsResponse.of(connections, dittoHeaders);
                });
    }

    private CompletionStage<RetrieveConnectionResponse> retrieveConnection(final RetrieveConnection retrieveConnection) {

        return askConnectivity(retrieveConnection);
    }

    private CompletionStage<RetrieveConnectionResponse> askConnectivity(final RetrieveConnection command) {

        logger.withCorrelationId(command).debug("Sending command <{}> to connectivity.service.", command);
        final var commandWithCorrelationId = ensureCommandHasCorrelationId(command);
        Duration askTimeout = initialCommand.getDittoHeaders().getTimeout().orElse(defaultTimeout);
        return Patterns.ask(edgeCommandForwarder, commandWithCorrelationId, askTimeout)
                .thenApply(response -> {
                    logger.withCorrelationId(command)
                            .debug("Received response <{}> from connectivity.service.", response);
                    throwCauseIfErrorResponse(response);
                    throwCauseIfDittoRuntimeException(response);
                    final RetrieveConnectionResponse mappedResponse =
                            mapToType(response, RetrieveConnectionResponse.class, command);
                    logger.withCorrelationId(command)
                            .info("Received response of type <{}> from connectivity.service.",
                                    mappedResponse.getType());
                    return mappedResponse;
                });
    }

    private static Command<?> ensureCommandHasCorrelationId(final Command<?> command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final Optional<String> correlationIdOptional = dittoHeaders.getCorrelationId();
        if (correlationIdOptional.isPresent()) {
            return command;
        }
        return command.setDittoHeaders(dittoHeaders.toBuilder().randomCorrelationId().build());
    }

    private static void throwCauseIfErrorResponse(final Object response) {
        if (response instanceof ErrorResponse<?> errorResponse) {
            throw (errorResponse).getDittoRuntimeException();
        }
    }

    private static void throwCauseIfDittoRuntimeException(final Object response) {
        if (response instanceof DittoRuntimeException dittoRuntimeException) {
            throw dittoRuntimeException;
        }
    }

    private static <T> T mapToType(final Object object, final Class<T> targetClass,
            final WithDittoHeaders withDittoHeaders) {

        final Class<?> actualClass = object.getClass();
        if (targetClass.isAssignableFrom(actualClass)) {
            return targetClass.cast(object);
        }
        final String message =
                String.format("Expected <%s> response but got <%s>!", targetClass.getSimpleName(), actualClass);
        final IllegalArgumentException exception = new IllegalArgumentException(message);
        throw ConnectivityInternalErrorException.newBuilder()
                .dittoHeaders(withDittoHeaders.getDittoHeaders())
                .cause(exception)
                .build();
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

    protected void stop() {
        getContext().stop(getSelf());
    }
}
