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
package org.eclipse.ditto.services.connectivity.messaging.httppush;

import org.eclipse.ditto.model.connectivity.Connection;

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
     * Specific config name for the HTTP method of the connection.
     */
    String METHOD = "method";

    /**
     * Specific config name for the amount of concurrent HTTP requests to make.
     */
    String PARALLELISM = "parallelism";

    /**
     * Specific config path for the method of test requests.
     */
    String TEST_METHOD = "test-method";

    /**
     * Specific config path for the expected status code of test requests.
     */
    String TEST_STATUS = "test-status";

    /**
     * Optional path of test requests.
     */
    String TEST_PATH = "test-path";

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
     * @param <T> type of correlation IDs.
     * @return flow from request-correlationId pairs to response-correlationId pairs.
     */
    <T> Flow<Pair<HttpRequest, T>, Pair<Try<HttpResponse>, T>, ?> createFlow(ActorSystem system, LoggingAdapter log);

    /**
     * Create an HTTP-push-factory from a valid HTTP-push connection
     * with undefined behavior if the connection is not valid.
     *
     * @param connection the connection.
     * @return the HTTP-push-factory.
     */
    static HttpPushFactory of(final Connection connection) {
        return DefaultHttpPushFactory.of(connection);
    }
}
