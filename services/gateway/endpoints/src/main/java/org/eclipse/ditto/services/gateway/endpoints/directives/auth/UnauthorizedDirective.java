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
