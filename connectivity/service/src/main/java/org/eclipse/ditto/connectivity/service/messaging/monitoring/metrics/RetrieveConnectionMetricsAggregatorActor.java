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
package org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics;

import java.time.Duration;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionTimeoutException;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.event.DiagnosticLoggingAdapter;

/**
 * An aggregation actor which receives {@link RetrieveConnectionMetricsResponse} messages and aggregates them into a
 * single {@link RetrieveConnectionMetricsResponse} message it sends back to a passed in {@code sender}.
 */
public final class RetrieveConnectionMetricsAggregatorActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final Connection connection;
    private final DittoHeaders originalHeaders;
    private int expectedResponses;
    private final ActorRef sender;
    private final Duration timeout;

    private RetrieveConnectionMetricsResponse theResponse;

    @SuppressWarnings("unused")
    private RetrieveConnectionMetricsAggregatorActor(final Connection connection, final ActorRef sender,
            final DittoHeaders originalHeaders, final Duration timeout) {
        this.connection = connection;
        this.originalHeaders = originalHeaders;

        // one RetrieveConnectionMetricsResponse per client actor
        this.expectedResponses = connection.getClientCount();
        this.sender = sender;
        this.timeout = timeout;
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the {@code Connection} for which to aggregate the metrics for.
     * @param sender the ActorRef of the sender to which to answer the response to.
     * @param originalHeaders the DittoHeaders to use for the response message.
     * @param timeout the timeout to apply in order to receive the response.
     * @return the Akka configuration Props object
     */
    public static Props props(final Connection connection, final ActorRef sender,
            final DittoHeaders originalHeaders, final Duration timeout) {
        return Props.create(RetrieveConnectionMetricsAggregatorActor.class, connection, sender, originalHeaders,
                timeout);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(RetrieveConnectionMetricsResponse.class, this::handleRetrieveConnectionMetricsResponse)
                .match(ReceiveTimeout.class, receiveTimeout -> this.handleReceiveTimeout())
                .matchAny(any -> log.info("Cannot handle {}", any.getClass())).build();
    }

    private void handleReceiveTimeout() {
        if (theResponse != null) {
            sendResponse();
        } else {
            sender.tell(
                    ConnectionTimeoutException.newBuilder(connection.getId(), RetrieveConnectionMetrics.TYPE)
                            .dittoHeaders(originalHeaders)
                            .build(),
                    getSender());
        }
        stopSelf();
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
            theResponse = ConnectivityCounterRegistry.mergeRetrieveConnectionMetricsResponse(
                    theResponse, retrieveConnectionMetricsResponse);
        }

        // if response is complete, send back to caller
        if (--expectedResponses == 0) {
            log.debug("Received all expected responses.");
            sendResponse();
            stopSelf();
        }
    }

    private void sendResponse() {
        sender.tell(theResponse.setDittoHeaders(originalHeaders), getSelf());
    }

    private void stopSelf() {
        getContext().cancelReceiveTimeout();
        getContext().stop(getSelf());
    }
}
