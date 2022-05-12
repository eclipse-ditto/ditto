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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionMetrics;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;

import akka.actor.ActorRef;
import akka.pattern.Patterns;

/**
 * Calculates the priority based on the sum of consumed and published messages of a connection.
 */
public final class UsageBasedPriorityProvider implements ConnectionPriorityProvider {

    private static final Duration RETRIEVE_METRICS_TIMEOUT = Duration.ofSeconds(3);

    private final ActorRef connectionPersistenceActor;
    private final DittoDiagnosticLoggingAdapter log;

    private UsageBasedPriorityProvider(final ActorRef connectionPersistenceActor,
            final DittoDiagnosticLoggingAdapter log) {
        this.connectionPersistenceActor = connectionPersistenceActor;
        this.log = log;
    }

    public static UsageBasedPriorityProvider getInstance(final ActorRef connectionPersistenceActor,
            final DittoDiagnosticLoggingAdapter log) {
        return new UsageBasedPriorityProvider(connectionPersistenceActor, log);
    }

    @Override
    public CompletionStage<Integer> getPriorityFor(final ConnectionId connectionId, final String correlationId) {
        final DittoHeaders headers = DittoHeaders.newBuilder().correlationId(correlationId).build();
        final RetrieveConnectionMetrics retrieveConnectionMetrics = RetrieveConnectionMetrics.of(connectionId, headers);
        return Patterns.ask(connectionPersistenceActor, retrieveConnectionMetrics, RETRIEVE_METRICS_TIMEOUT)
                .handle((metrics, error) -> {
                    if (metrics instanceof RetrieveConnectionMetricsResponse metricsResponse) {
                        final ConnectionMetrics connectionMetrics = metricsResponse.getConnectionMetrics();
                        return ConnectionPriorityCalculator.calculatePriority(connectionMetrics);
                    } else if (error != null) {
                        log.withCorrelationId(correlationId)
                                .warning("Got error when trying to retrieve the connection metrics: <{}>",
                                        error.getMessage());
                    } else {
                        log.withCorrelationId(correlationId)
                                .warning("Expected <{}> but got <{}>", RetrieveConnectionMetricsResponse.class,
                                        metrics.getClass());
                    }
                    return 0;
                });
    }

}
