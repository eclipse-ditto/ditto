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
import java.util.Optional;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionTimeoutException;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.event.DiagnosticLoggingAdapter;

public class RetrieveConnectionMetricsAggregatorActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private static final long DEFAULT_TIMEOUT = Duration.ofSeconds(10).toMillis();
    private final Connection connection;
    private final DittoHeaders dittoHeaders;
    private int expectedResponses;
    private final ActorRef sender;
    private final Duration timeout;

    private RetrieveConnectionMetricsResponse theResponse;

    private RetrieveConnectionMetricsAggregatorActor(final Connection connection, final ActorRef sender,
            final DittoHeaders originalHeaders) {
        this.connection = connection;
        this.dittoHeaders = originalHeaders;

        // one RetrieveConnectionMetricsResponse per client actor
        this.expectedResponses = connection.getClientCount();
        this.sender = sender;
        this.timeout = extractTimeoutFromCommand(dittoHeaders);
    }

    public static Props props(final Connection connection, final ActorRef sender,
            final DittoHeaders originalHeaders) {
        return Props.create(RetrieveConnectionMetricsAggregatorActor.class, connection, sender, originalHeaders);
    }

    private Duration extractTimeoutFromCommand(final DittoHeaders headers) {
        return Duration.ofMillis(Optional.ofNullable(headers.get("timeout"))
                .map(Long::parseLong)
                .orElse(DEFAULT_TIMEOUT));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(RetrieveConnectionMetricsResponse.class, this::handleRetrieveConnectionMetricsResponse)
                .match(ReceiveTimeout.class, this::handleReceiveTimeout)
                .matchAny(any -> log.info("Cannot handle {}", any.getClass())).build();
    }

    private void handleReceiveTimeout(final ReceiveTimeout receiveTimeout) {
        if (theResponse != null) {
            sendResponse();
        } else {
            sender.tell(
                    ConnectionTimeoutException.newBuilder(connection.getId(), RetrieveConnectionMetrics.TYPE).build(),
                    getSender());
        }
    }

    @Override
    public void preStart() {
        getContext().setReceiveTimeout(timeout);
    }

    private void handleRetrieveConnectionMetricsResponse(
            final RetrieveConnectionMetricsResponse retrieveConnectionMetricsResponse) {

        log.debug("Received RetrieveConnectionMetricsResponse from {}: {}", getSender(),
                retrieveConnectionMetricsResponse.toJsonString());

        if (theResponse == null) {
            theResponse = retrieveConnectionMetricsResponse;
        } else {
            log.debug("Current response: {}", theResponse.toJsonString());
            theResponse = theResponse.mergeWith(retrieveConnectionMetricsResponse);
            log.debug("Merged response: {}", theResponse.toJsonString());
        }

        // if response is complete, send back to caller
        if (--expectedResponses == 0) {
            log.debug("Received all expected responses.");
            sendResponse();
        }
    }

    private void sendResponse() {
        sender.tell(theResponse.setDittoHeaders(dittoHeaders), getSelf());
    }
}
