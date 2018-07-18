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

import static akka.http.javadsl.server.Directives.mapRouteResult;

import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.gateway.endpoints.utils.DirectivesLoggingUtils;
import org.eclipse.ditto.services.gateway.starter.service.util.HttpEntityReader;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Complete;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.server.RouteResult;
import akka.http.javadsl.server.RouteResults;
import akka.stream.ActorMaterializer;
import akka.util.ByteString;

/**
 * Custom Akka Http directive which rewrites the response for the end user, e.g. by returning 503 instead of 5xx in
 * case of error.
 */
public final class ResponseRewritingDirective {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResponseRewritingDirective.class);

    static final Complete UNAVAILABLE_ROUTE_RESULT = createUnavailableRouteResult();
    static final Complete INTERNAL_SERVER_ERROR_RESULT = createInternalServerErrorResult();

    private ResponseRewritingDirective() {
        // no op
    }

    /**
     * Rewrites the response if necessary, e.g. by returning 503 instead of 5xx.
     *
     * @param materializer the actor materializer of the actor system.
     * @param correlationId the correlationId which will be added to the log
     * @param inner the inner Route to wrap with the response headers
     * @return the new Route wrapping {@code inner} with the response headers
     */
    public static Route rewriteResponse(final ActorMaterializer materializer,
            final String correlationId, final Supplier<Route> inner) {
        final Function<RouteResult, RouteResult> routeResultRouteResultFunction = routeResult -> {
            LOGGER.debug("Got RouteResult '{}'", routeResult);
            if (routeResult instanceof Complete) {
                final Complete complete = (Complete) routeResult;
                return handleCompleteResult(materializer, complete);
            } else {
                /* routeResult could be Rejected, if no route is able to handle the request -> but this should
                   not happen when rejections are handled before this directive is called. */
                return handleNonCompleteResult(routeResult);

            }
        };
        return mapRouteResult(
                DirectivesLoggingUtils.enhanceLogWithCorrelationId(correlationId, () -> routeResultRouteResultFunction),
                inner);
    }

    private static RouteResult handleNonCompleteResult(final RouteResult routeResult) {
        LOGGER.warn("Unexpected routeResult '{}', will be handled by " +
                "akka default RejectionHandler.", routeResult);
        return routeResult;
    }

    private static RouteResult handleCompleteResult(final ActorMaterializer materializer, final Complete complete) {
        final int statusCode = complete.getResponse().status().intValue();
        final RouteResult result;

        if (statusCode == HttpStatusCode.INTERNAL_SERVER_ERROR.toInt()) {
            LOGGER.debug("RouteResult with status {} is rewritten to: '{}'", statusCode, UNAVAILABLE_ROUTE_RESULT);
            result = UNAVAILABLE_ROUTE_RESULT;
        } else if (statusCode <= HttpStatusCode.GATEWAY_TIMEOUT.toInt()) {
            LOGGER.debug("RouteResult with status {} is not rewritten", statusCode);
            result = complete;
        } else {
            final String entity = HttpEntityReader.entityToString(complete.getResponse().entity(), materializer);
            if (isKnownException(entity)) {
                LOGGER.debug("RouteResult with status {} is not rewritten", statusCode);
                result = complete;
            } else {
                LOGGER.warn("Status {} is unknown, RouteResult is rewritten to: '{}'", statusCode,
                        INTERNAL_SERVER_ERROR_RESULT);
                result = INTERNAL_SERVER_ERROR_RESULT;
            }
        }

        return result;
    }

    private static boolean isKnownException(final String entity) {
        try {
            final JsonObject jsonObject = JsonFactory.newObject(entity);
            return jsonObject.contains(DittoRuntimeException.JsonFields.ERROR_CODE.getPointer());
        } catch (final RuntimeException e) {
            return false;
        }
    }

    private static Complete createUnavailableRouteResult() {
        return createExceptionRouteResult(GatewayServiceUnavailableException.newBuilder().build());
    }

    private static Complete createInternalServerErrorResult() {
        return createExceptionRouteResult(GatewayInternalErrorException.newBuilder().build());
    }

    private static Complete createExceptionRouteResult(final DittoRuntimeException dre) {
        return RouteResults.complete(HttpResponse.create().withStatus(dre.getStatusCode().toInt())
                .withEntity(ContentTypes.APPLICATION_JSON, ByteString.fromString(dre.toJsonString())));
    }

}
