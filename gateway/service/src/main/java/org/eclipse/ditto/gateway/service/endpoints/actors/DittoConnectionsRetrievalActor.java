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
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;

/**
 * Actor for retrieving multiple connections.
 */
public class DittoConnectionsRetrievalActor extends AbstractConnectionsRetrievalActor {

    @SuppressWarnings("unused")
    private DittoConnectionsRetrievalActor(final ActorRef edgeCommandForwarder, final ActorRef sender,
            final Duration timeout) {
        super(edgeCommandForwarder, sender);
    }

    /**
     * Creates props for {@code ConnectionsRetrievalActor}.
     *
     * @param edgeCommandForwarder the edge command forwarder.
     * @param sender the initial sender.
     * @return the props.
     */
    public static Props props(final ActorRef edgeCommandForwarder, final ActorRef sender) {
        return Props.create(DittoConnectionsRetrievalActor.class, edgeCommandForwarder, sender);
    }

    @Override
    protected void retrieveConnections(final RetrieveConnections retrieveConnections) {
        this.edgeCommandForwarder.tell(RetrieveAllConnectionIds.of(retrieveConnections.getDittoHeaders()), getSelf());
    }

    @Override
    protected Receive commandResponseAwaitingBehaviour() {
        return ReceiveBuilder.create()
                .match(RetrieveAllConnectionIdsResponse.class, this::retrieveConnectionsById)
                .build();
    }

    private void retrieveConnectionsById(RetrieveAllConnectionIdsResponse msg) {
        final var connectionIds = msg.getAllConnectionIds();
        if (initialCommand.getIdsOnly()) {
            RetrieveConnectionsResponse response = RetrieveConnectionsResponse
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
                        .toList(), initialCommand.getDittoHeaders())
                        .thenAccept(retrieveConnectionsResponse -> {
                            sender.tell(retrieveConnectionsResponse, getSelf());
                            stop();
                        })
                        .exceptionally(t -> {
                            ConnectivityInternalErrorException exception =
                                    ConnectivityInternalErrorException.newBuilder()
                                            .dittoHeaders(initialCommand.getDittoHeaders())
                                            .cause(t)
                                            .build();
                            ConnectivityErrorResponse response =
                                    ConnectivityErrorResponse.of(exception, initialCommand.getDittoHeaders());
                            sender.tell(response, getSelf());
                            stop();
                            return null;
                        });
            }
        }
    }

    private CompletionStage<RetrieveConnectionsResponse> retrieveConnections(
            final Collection<ConnectionId> connectionIds, final DittoHeaders dittoHeaders) {

        checkNotNull(connectionIds, "connectionIds");
        checkNotNull(dittoHeaders, "dittoHeaders");

        final List<CompletableFuture<RetrieveConnectionResponse>> completableFutures = connectionIds.parallelStream()
                .map(connectionId -> retrieveConnection(RetrieveConnection.of(connectionId, dittoHeaders)))
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

    private CompletionStage<RetrieveConnectionResponse> retrieveConnection(
            final RetrieveConnection retrieveConnection) {

        return askConnectivity(retrieveConnection);
    }

    private CompletionStage<RetrieveConnectionResponse> askConnectivity(
            final RetrieveConnection command) {

        logger.withCorrelationId(command).debug("Sending command <{}> to org.eclipse.ditto.connectivity.service.", command);
        final var commandWithCorrelationId = ensureCommandHasCorrelationId(command);
        Duration askTimeout = initialCommand.getDittoHeaders().getTimeout().orElse(defaultTimeout);
        return Patterns.ask(edgeCommandForwarder, commandWithCorrelationId, askTimeout)
                .thenApply(response -> {
                    logger.withCorrelationId(command)
                            .debug("Received response <{}> from org.eclipse.ditto.connectivity.service.", response);
                    throwCauseIfErrorResponse(response);
                    throwCauseIfDittoRuntimeException(response);
                    final RetrieveConnectionResponse mappedResponse =
                            mapToType(response, RetrieveConnectionResponse.class, command);
                    logger.withCorrelationId(command)
                            .info("Received response of type <{}> from org.eclipse.ditto.connectivity.service.",
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
}
