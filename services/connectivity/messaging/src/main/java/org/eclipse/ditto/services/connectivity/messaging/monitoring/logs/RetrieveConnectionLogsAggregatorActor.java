/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.monitoring.logs;

import java.time.Duration;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionTimeoutException;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionLogsResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.event.DiagnosticLoggingAdapter;

/**
 * An aggregation actor which receives {@link org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse} messages and aggregates them into a
 * single {@link org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse} message it sends back to a passed in {@code sender}.
 */
// TODO: docs & test
public final class RetrieveConnectionLogsAggregatorActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final Connection connection;
    private final DittoHeaders originalHeaders;
    private int expectedResponses;
    private final ActorRef sender;
    private final Duration timeout;

    private RetrieveConnectionLogsResponse theResponse;

    @SuppressWarnings("unused")
    private RetrieveConnectionLogsAggregatorActor(final Connection connection, final ActorRef sender,
            final DittoHeaders originalHeaders, final Duration timeout) {
        this.connection = connection;
        this.originalHeaders = originalHeaders;

        // one RetrieveConnectionLogsResponse per client actor
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
        return Props.create(RetrieveConnectionLogsAggregatorActor.class, connection, sender, originalHeaders,
                timeout);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(RetrieveConnectionLogsResponse.class, this::handleRetrieveConnectionLogsResponse)
                .match(ReceiveTimeout.class, receiveTimeout -> this.handleReceiveTimeout())
                .matchAny(any -> log.info("Cannot handle {}", any.getClass())).build();
    }

    private void handleReceiveTimeout() {
        if (theResponse != null) {
            sendResponse();
        } else {
            sender.tell(
                    ConnectionTimeoutException.newBuilder(connection.getId(), RetrieveConnectionLogs.TYPE)
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

    private void handleRetrieveConnectionLogsResponse(
            final RetrieveConnectionLogsResponse retrieveConnectionLogsResponse) {

        log.debug("Received RetrieveConnectionLogsResponse from {}: {}", getSender(),
                retrieveConnectionLogsResponse.toJsonString());

        if (theResponse == null) {
            theResponse = retrieveConnectionLogsResponse;
        } else {
            theResponse = RetrieveConnectionLogsResponse.mergeRetrieveConnectionLogsResponse(
                    theResponse, retrieveConnectionLogsResponse);
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
        getContext().stop(getSelf());
    }

}
