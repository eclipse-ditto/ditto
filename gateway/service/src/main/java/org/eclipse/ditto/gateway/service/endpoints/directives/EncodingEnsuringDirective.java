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
package org.eclipse.ditto.gateway.service.endpoints.directives;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.extractRequestContext;

import java.text.MessageFormat;
import java.util.function.Supplier;

import org.eclipse.ditto.gateway.service.endpoints.utils.HttpUtils;
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

    private static final String URI_INVALID_TEMPLATE =
            "URI ''{0}'' contains invalid characters. Please encode your URI according to RFC 3986.";

    private EncodingEnsuringDirective() {
        throw new AssertionError();
    }

    public static Route ensureEncoding(final Supplier<Route> inner) {
        return extractRequestContext(requestContext -> {
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
        });
    }
}
