/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.persistence;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionMetrics;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;

import akka.actor.ActorRef;
import akka.pattern.Patterns;

/**
 * Calculates the priority based on the sum of consumed and published messages of a connection.
 */
final class UsageBasedPriorityProvider implements ConnectionPriorityProvider {

    private static final Duration RETRIEVE_METRICS_TIMEOUT = Duration.ofSeconds(3);
    private final ActorRef connectionPersistenceActor;
    private final DittoDiagnosticLoggingAdapter log;

    UsageBasedPriorityProvider(final ActorRef connectionPersistenceActor, final DittoDiagnosticLoggingAdapter log) {
        this.connectionPersistenceActor = connectionPersistenceActor;
        this.log = log;
    }

    @Override
    public CompletionStage<Optional<Integer>> getPriorityFor(final ConnectionId connectionId,
            final String correlationId) {
        final DittoHeaders headers = DittoHeaders.newBuilder().correlationId(correlationId).build();
        final RetrieveConnectionMetrics retrieveConnectionMetrics = RetrieveConnectionMetrics.of(connectionId, headers);
        return Patterns.ask(connectionPersistenceActor, retrieveConnectionMetrics, RETRIEVE_METRICS_TIMEOUT)
                .handle((metrics, error) -> {
                    if (metrics instanceof RetrieveConnectionMetricsResponse) {
                        final ConnectionMetrics connectionMetrics =
                                ((RetrieveConnectionMetricsResponse) metrics).getConnectionMetrics();
                        return Optional.of(ConnectionPriorityCalculator.calculatePriority(connectionMetrics));
                    } else if (error != null) {
                        log.withCorrelationId(correlationId)
                                .warning("Got error when trying to retrieve the connection metrics: <{}>",
                                        error.getMessage());
                    } else {
                        log.withCorrelationId(correlationId)
                                .warning("Expected <{}> but got <{}>", RetrieveConnectionMetricsResponse.class,
                                        metrics.getClass());
                    }
                    return Optional.empty();
                });
    }

}
