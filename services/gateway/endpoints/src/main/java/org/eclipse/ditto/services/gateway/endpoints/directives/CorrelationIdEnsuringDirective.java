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
package org.eclipse.ditto.services.gateway.endpoints.directives;

import static akka.http.javadsl.server.Directives.extractRequestContext;
import static org.eclipse.ditto.services.gateway.endpoints.utils.DirectivesLoggingUtils.enhanceLogWithCorrelationId;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.server.Route;

/**
 * Custom Akka Http directive adding a correlationId to the request, if it does not yet exist.
 */
public final class CorrelationIdEnsuringDirective {

    private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationIdEnsuringDirective.class);

    private static final String CORRELATION_ID_HEADER =
            org.eclipse.ditto.services.gateway.security.HttpHeader.X_CORRELATION_ID.getName();

    private CorrelationIdEnsuringDirective() {
        // no op
    }

    /**
     * Extracts the correlationId from the request or creates a new one, if it does not exist.
     *
     * @param inner the inner Route to provide with the correlationId
     * @return the new Route wrapping {@code inner} with the correlationId
     */
    public static Route ensureCorrelationId(final Function<String, Route> inner) {
        return extractRequestContext(requestContext -> {
            final HttpRequest request = requestContext.getRequest();
            final Optional<String> correlationIdOpt = extractCorrelationId(request);
            final String correlationId;
            if (correlationIdOpt.isPresent()) {
                correlationId = correlationIdOpt.get();
                LOGGER.debug("CorrelationId already exists in request: {}", correlationId);
            } else {
                correlationId = UUID.randomUUID().toString();
                LOGGER.debug("Created new CorrelationId: {}", correlationId);
            }

            return enhanceLogWithCorrelationId(correlationId, () -> inner.apply(correlationId));
        });
    }

    private static Optional<String> extractCorrelationId(final HttpRequest request) {
        return request.getHeader(CORRELATION_ID_HEADER).map(HttpHeader::value);
    }

}
