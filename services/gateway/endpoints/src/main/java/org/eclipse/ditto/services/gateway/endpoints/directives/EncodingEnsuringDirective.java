/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.endpoints.directives;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.extractRequestContext;
import static org.eclipse.ditto.services.gateway.endpoints.utils.DirectivesLoggingUtils.enhanceLogWithCorrelationId;

import java.text.MessageFormat;
import java.util.function.Supplier;

import org.eclipse.ditto.services.gateway.endpoints.utils.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.Route;
import akka.http.scaladsl.model.IllegalUriException;

/**
 * Custom Akka Http directive ensuring that request uris are encoded.
 */
public final class EncodingEnsuringDirective {

    private static final Logger LOGGER = LoggerFactory.getLogger(EncodingEnsuringDirective.class);

    private static final String URI_INVALID_TEMPLATE = "URI ''{0}'' contains invalid characters. Please encode your URI" +
            " according to RFC 3986.";

    private EncodingEnsuringDirective() {
        // no op
    }

    public static Route ensureEncoding(final String correlationId, final Supplier<Route> inner) {
        return extractRequestContext(requestContext -> enhanceLogWithCorrelationId(correlationId, () -> {
            final Uri uri = requestContext.getRequest().getUri();
            try {
                // per default, Akka evaluates the query params "lazily" in the routes and throws an IllegalUriException
                // in case of error; we evaluate the query params explicitly here to be able to handle this error at
                // a central location
                uri.query();
            } catch (final IllegalUriException e) {
                LOGGER.debug("URI parsing failed", e);

                final String rawRequestUri = HttpUtils.getRawRequestUri(requestContext.getRequest());
                final String message = MessageFormat.format(URI_INVALID_TEMPLATE, rawRequestUri);
                return complete(StatusCodes.BAD_REQUEST, message);
            }

            return inner.get();
        }));
    }
}
