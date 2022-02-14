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
package org.eclipse.ditto.connectivity.service.messaging.httppush;

import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.HttpPushConfig;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelState;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.PreparedTimer;

import akka.actor.ActorSystem;
import akka.event.LoggingAdapter;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.Pair;
import akka.stream.javadsl.Flow;
import scala.util.Try;

/**
 * Factory of HTTP requests and request handling flows.
 */
public interface HttpPushFactory {

    /**
     * Create a request template without headers or payload for an HTTP publish target.
     * Published external messages set the headers and payload.
     *
     * @param httpPublishTarget the HTTP publish target.
     * @return the HTTP request for the target.
     */
    HttpRequest newRequest(HttpPublishTarget httpPublishTarget);

    /**
     * Create a flow to send HTTP(S) requests.
     *
     * @param system the actor system with the default Akka HTTP configuration.
     * @param log logger for the flow.
     * @param requestTimeout timeout of each request.
     * @return flow from request-correlationId pairs to response-correlationId pairs.
     */
    default Flow<Pair<HttpRequest, HttpPushContext>, Pair<Try<HttpResponse>, HttpPushContext>, ?> createFlow(
            final ActorSystem system,
            final LoggingAdapter log,
            final Duration requestTimeout) {

        return createFlow(system, log, requestTimeout, null, null);
    }

    /**
     * Create a flow to send HTTP(S) requests.
     *
     * @param system the actor system with the default Akka HTTP configuration.
     * @param log logger for the flow.
     * @param requestTimeout timeout of each request.
     * @param timer timer to measure HTTP requests.
     * @param durationConsumer consumer of measured HTTP request durations.
     * @return flow from request-correlationId pairs to response-correlationId pairs.
     */
    Flow<Pair<HttpRequest, HttpPushContext>, Pair<Try<HttpResponse>, HttpPushContext>, ?> createFlow(ActorSystem system,
            LoggingAdapter log, Duration requestTimeout, @Nullable PreparedTimer timer,
            @Nullable BiConsumer<Duration, ConnectionMonitor.InfoProvider> durationConsumer);

    /**
     * Create an HTTP-push-factory from a valid HTTP-push connection
     * with undefined behavior if the connection is not valid.
     *
     * @param connection the connection.
     * @param httpPushConfig configuration of Http connections.
     * @param connectionLogger the connection logger
     * @param tunnelConfigSupplier a supplier of the SshTunnelState
     * @return the HTTP-push-factory.
     */
    static HttpPushFactory of(final Connection connection, final HttpPushConfig httpPushConfig,
            final ConnectionLogger connectionLogger, final Supplier<SshTunnelState> tunnelConfigSupplier) {
        return DefaultHttpPushFactory.of(connection, httpPushConfig, connectionLogger, tunnelConfigSupplier);
    }

}
