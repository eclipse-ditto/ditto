/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.metrics;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatusResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.event.DiagnosticLoggingAdapter;

public class RetrieveConnectionStatusAggregatorActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final Duration timeout;
    private final Map<ResourceStatus.ResourceType, Integer> expectedResponses;
    private final ActorRef sender;

    private RetrieveConnectionStatusResponse theResponse;

    private RetrieveConnectionStatusAggregatorActor(final Connection connection,
            final ActorRef sender, final DittoHeaders dittoHeaders, final Duration timeout) {
        this.timeout = timeout;
        this.sender = sender;
        theResponse = RetrieveConnectionStatusResponse.of(connection.getId(), connection.getConnectionStatus(),
                        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), dittoHeaders);

        this.expectedResponses = new HashMap<>();
        // one response per client actor
        expectedResponses.put(ResourceStatus.ResourceType.CLIENT, connection.getClientCount());
        if (ConnectionStatus.OPEN.equals(connection.getConnectionStatus())) {
            // one response per source/target
            expectedResponses.put(ResourceStatus.ResourceType.TARGET,
                    // currently there is always only one publisher per client
                    connection.getClientCount());
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

    public static Props props(final Connection connection, final ActorRef sender, final DittoHeaders dittoHeaders,
            final Duration timeout) {
        return Props.create(RetrieveConnectionStatusAggregatorActor.class, connection, sender, dittoHeaders, timeout);
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
        expectedResponses.compute(resourceStatus.getResourceType(), (type, count)->count-1);
        log.debug("Received resource status from {}: {}", getSender(), resourceStatus);
        // aggregate status...
        theResponse = theResponse.withAddressStatus(resourceStatus);

        // if response is complete, send back to caller
        if (getRemainingResponses() == 0) {
            sendResponse();
        }
    }

    private int getRemainingResponses() {
        return expectedResponses.values().stream().mapToInt(i->i).sum();
    }

    private void sendResponse() {
        sender.tell(theResponse, getSelf());
        stopSelf();
    }

    private void stopSelf() {
        getContext().stop(getSelf());
    }
}
