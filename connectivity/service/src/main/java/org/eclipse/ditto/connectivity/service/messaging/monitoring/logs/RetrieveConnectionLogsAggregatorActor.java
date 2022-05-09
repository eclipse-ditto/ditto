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
package org.eclipse.ditto.connectivity.service.messaging.monitoring.logs;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionTimeoutException;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionLogs;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionLogsResponse;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.event.DiagnosticLoggingAdapter;

/**
 * An aggregation actor which receives {@link org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionLogs}
 * messages and aggregates them into a single {@link org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionLogsResponse}
 * message it sends back to a passed in {@code sender}.
 */
public final class RetrieveConnectionLogsAggregatorActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final Connection connection;
    private final DittoHeaders originalHeaders;
    private int expectedResponses;
    private final ActorRef sender;
    private final Duration timeout;
    private final long maximumLogSizeInByte;

    private RetrieveConnectionLogsResponse theResponse;

    @SuppressWarnings("unused")
    private RetrieveConnectionLogsAggregatorActor(final Connection connection, final ActorRef sender,
            final DittoHeaders originalHeaders, final Duration timeout, final long maxLogSizeBytes) {

        this.connection = connection;
        this.originalHeaders = originalHeaders;

        // one RetrieveConnectionLogsResponse per client actor
        this.expectedResponses = connection.getClientCount();
        this.sender = sender;
        this.timeout = timeout;
        this.maximumLogSizeInByte = maxLogSizeBytes;
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the {@code Connection} for which to aggregate the logs for.
     * @param sender the ActorRef of the sender to which to answer the response to.
     * @param originalHeaders the DittoHeaders to use for the response message.
     * @param timeout the timeout to apply in order to receive the response.
     * @param maxLogSizeBytes the maximum length of all log entries JSON representation.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, final ActorRef sender,
            final DittoHeaders originalHeaders, final Duration timeout, final long maxLogSizeBytes) {

        return Props.create(RetrieveConnectionLogsAggregatorActor.class, connection, sender, originalHeaders,
                timeout, maxLogSizeBytes);
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
        final RetrieveConnectionLogsResponse restrictedResponse = restrictMaxLogEntriesLength();
        sender.tell(restrictedResponse.setDittoHeaders(originalHeaders), getSelf());
    }

    // needed so that the logs fit into the max cluster message size
    private RetrieveConnectionLogsResponse restrictMaxLogEntriesLength() {
        final ConnectionId connectionId = theResponse.getEntityId();
        final List<LogEntry> originalLogEntries = theResponse.getConnectionLogs().stream()
                .sorted(Comparator.comparing(LogEntry::getTimestamp).reversed())
                .toList();

        final List<LogEntry> restrictedLogs = new ArrayList<>();
        long currentSize = 0;
        for (final LogEntry logEntry : originalLogEntries) {
            final long sizeOfLogEntry = logEntry.toJsonString().length();
            final long sizeWithNextEntry = currentSize + sizeOfLogEntry;
            if (sizeWithNextEntry > maximumLogSizeInByte) {
                log.info("Dropping <{}> of <{}> log entries for connection with ID <{}>, because of size limit.",
                        originalLogEntries.size() - restrictedLogs.size(), originalLogEntries.size(), connectionId);
                break;
            }
            restrictedLogs.add(logEntry);
            currentSize = sizeWithNextEntry;
        }
        Collections.reverse(restrictedLogs);
        return RetrieveConnectionLogsResponse.of(connectionId, restrictedLogs,
                theResponse.getEnabledSince().orElse(null), theResponse.getEnabledUntil().orElse(null),
                theResponse.getDittoHeaders());
    }

    private void stopSelf() {
        getContext().cancelReceiveTimeout();
        getContext().stop(getSelf());
    }

}
