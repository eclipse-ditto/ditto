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
package org.eclipse.ditto.services.connectivity.messaging.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatusResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.event.DiagnosticLoggingAdapter;

/**
 * An aggregation actor which receives {@link ResourceStatus} messages from all {@code clients, targets and sources}
 * and aggregates them into a single {@link RetrieveConnectionStatusResponse} message it sends back to a passed in
 * {@code sender}.
 */
public final class RetrieveConnectionStatusAggregatorActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final Duration timeout;
    private final Map<ResourceStatus.ResourceType, Integer> expectedResponses;
    private final ActorRef sender;

    private RetrieveConnectionStatusResponse.Builder theResponse;

    @SuppressWarnings("unused")
    private RetrieveConnectionStatusAggregatorActor(final Connection connection,
            final ActorRef sender, final DittoHeaders originalHeaders, final Duration timeout) {
        this.timeout = timeout;
        this.sender = sender;
        theResponse = RetrieveConnectionStatusResponse.getBuilder(connection.getId(), originalHeaders)
                .connectionStatus(connection.getConnectionStatus())
                .liveStatus(ConnectivityStatus.UNKNOWN)
                .connectedSince(Instant.EPOCH)
                .clientStatus(Collections.emptyList())
                .sourceStatus(Collections.emptyList())
                .targetStatus(Collections.emptyList());

        expectedResponses = new EnumMap<>(ResourceStatus.ResourceType.class);
        // one response per client actor
        expectedResponses.put(ResourceStatus.ResourceType.CLIENT, connection.getClientCount());
        if (ConnectivityStatus.OPEN.equals(connection.getConnectionStatus())) {
            // one response per source/target
            expectedResponses.put(ResourceStatus.ResourceType.TARGET,
                    connection.getTargets()
                            .stream()
                            .mapToInt(target ->
                                    connection.getClientCount())
                            .sum());
            expectedResponses.put(ResourceStatus.ResourceType.SOURCE,
                    connection.getSources()
                            .stream()
                            .mapToInt(source ->
                                    connection.getClientCount()
                                            * source.getConsumerCount()
                                            * source.getAddresses().size())
                            .sum());
        }
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the {@code Connection} for which to aggregate the status for.
     * @param sender the ActorRef of the sender to which to answer the response to.
     * @param originalHeaders the DittoHeaders to use for the response message.
     * @param timeout the timeout to apply in order to receive the response.
     * @return the Akka configuration Props object
     */
    public static Props props(final Connection connection, final ActorRef sender, final DittoHeaders originalHeaders,
            final Duration timeout) {
        return Props.create(RetrieveConnectionStatusAggregatorActor.class, connection, sender, originalHeaders, timeout);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ResourceStatus.class, this::handleResourceStatus)
                .match(ReceiveTimeout.class, this::handleReceiveTimeout)
                .matchAny(any -> log.info("Cannot handle {}", any.getClass())).build();
    }

    private void handleReceiveTimeout(final ReceiveTimeout receiveTimeout) {
        log.debug("RetrieveConnectionStatus timed out, sending (partial) response.");
        sendResponse();
        stopSelf();
    }

    @Override
    public void preStart() {
        getContext().setReceiveTimeout(timeout);
    }

    private void handleResourceStatus(final ResourceStatus resourceStatus) {
        expectedResponses.compute(resourceStatus.getResourceType(), (type, count)-> count == null ? 0 : count-1);
        log.debug("Received resource status from {}: {}", getSender(), resourceStatus);
        // aggregate status...
        theResponse.withAddressStatus(resourceStatus);

        // if response is complete, send back to caller
        if (getRemainingResponses() == 0) {
            sendResponse();
        }
    }

    private int getRemainingResponses() {
        return expectedResponses.values().stream()
                .mapToInt(i->i)
                .sum();
    }

    private void sendResponse() {
        final List<ResourceStatus> clientStatus = theResponse.build().getClientStatus();
        final boolean anyClientOpen = clientStatus.stream()
                .map(ResourceStatus::getStatus)
                .anyMatch(ConnectivityStatus.OPEN::equals);
        final boolean anyClientFailed = clientStatus.stream()
                .map(ResourceStatus::getStatus)
                .anyMatch(ConnectivityStatus.FAILED::equals);
        final boolean allClientsClosed = clientStatus.stream()
                .map(ResourceStatus::getStatus)
                .allMatch(ConnectivityStatus.CLOSED::equals);

        final ConnectivityStatus liveStatus;
        if (anyClientOpen) {
            liveStatus = ConnectivityStatus.OPEN;
        } else {
            if (anyClientFailed) {
                liveStatus = ConnectivityStatus.FAILED;
            } else if (allClientsClosed) {
                liveStatus = ConnectivityStatus.CLOSED;
            } else {
                liveStatus = ConnectivityStatus.UNKNOWN;
            }
        }

        final Optional<Instant> earliestConnectedSince = clientStatus.stream()
                .filter(rs -> ConnectivityStatus.OPEN.equals(rs.getStatus()))
                .map(ResourceStatus::getInStateSince)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(Instant::compareTo);

        theResponse
                .connectedSince(earliestConnectedSince.orElse(null))
                .liveStatus(liveStatus);
        sender.tell(theResponse.build(), getSelf());
        stopSelf();
    }

    private void stopSelf() {
        getContext().stop(getSelf());
    }
}
