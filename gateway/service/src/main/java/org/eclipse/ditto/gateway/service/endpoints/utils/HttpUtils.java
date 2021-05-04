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
package org.eclipse.ditto.gateway.service.endpoints.utils;

import javax.annotation.concurrent.Immutable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.headers.RawRequestURI;

/**
 * Utilities for Akka-Http.
 */
@Immutable
public final class HttpUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpUtils.class);

    private HttpUtils() {
        throw new AssertionError();
    }

    /**
     * Gets the (absolute) raw requestUri.
     * <p>NOTE: You must configure {@code raw-request-uri-header = on} for Akka-Http, otherwise the url already
     * parsed by Akka Http will be returned.</p>
     *
     * @param request the http request
     * @return the raw requestUri
     */
    public static String getRawRequestUri(final HttpRequest request) {
        return request.getHeader(RawRequestURI.class)
                .map(RawRequestURI::toString)
                .orElseGet(() -> {
                    LOGGER.warn("raw-request-uri-header is not enabled, returning already parsed URI");
                    return request.getUri().toString();
                });
    }

}
