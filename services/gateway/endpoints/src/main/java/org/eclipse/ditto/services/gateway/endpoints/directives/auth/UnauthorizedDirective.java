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
package org.eclipse.ditto.services.gateway.endpoints.directives.auth;

import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;

/**
 * Ditto's default directive for handling unauthorized requests.
 */
public final class UnauthorizedDirective implements Function<String, Route> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnauthorizedDirective.class);
    private static final StatusCode STATUS_CODE = StatusCodes.UNAUTHORIZED;

    @Override
    public Route apply(final String correlationId) {
        LOGGER.debug("Returning status {}", STATUS_CODE);
        return Directives.complete(STATUS_CODE);
    }
}
