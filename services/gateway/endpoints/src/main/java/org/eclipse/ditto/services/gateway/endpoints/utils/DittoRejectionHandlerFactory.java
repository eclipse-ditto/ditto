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
package org.eclipse.ditto.services.gateway.endpoints.utils;

import static akka.http.javadsl.server.Directives.complete;

import java.text.MessageFormat;

import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.MissingQueryParamRejection;
import akka.http.javadsl.server.RejectionHandler;
import akka.http.javadsl.server.Route;

/**
 * Factory to create a custom {@link akka.http.javadsl.server.RejectionHandler}.
 */
public final class DittoRejectionHandlerFactory {

    private static final String MISSING_QUERY_PARAM_TEMPLATE = "Request is missing required query parameter ''{0}''";

    private DittoRejectionHandlerFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a new instance of {@link akka.http.javadsl.server.RejectionHandler} with custom behaviour.
     *
     * @return The new instance.
     */
    public static RejectionHandler createInstance() {
        return RejectionHandler.newBuilder()
                .handle(MissingQueryParamRejection.class, DittoRejectionHandlerFactory::handleMissingQueryParam)
                .build()
                .withFallback(RejectionHandler.defaultHandler());
    }

    private static Route handleMissingQueryParam(MissingQueryParamRejection missingQueryParamRejection) {
        // return status code 400 instead of the akka default 404
        return complete(StatusCodes.BAD_REQUEST,
                MessageFormat.format(MISSING_QUERY_PARAM_TEMPLATE, missingQueryParamRejection.parameterName()));
    }
}
