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
import static akka.http.javadsl.server.Directives.extractActorSystem;
import static akka.http.javadsl.server.Directives.options;
import static akka.http.javadsl.server.Directives.respondWithHeaders;
import static akka.http.javadsl.server.Directives.route;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.ditto.services.gateway.starter.service.util.ConfigKeys;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.AccessControlAllowMethods;
import akka.http.javadsl.model.headers.AccessControlAllowOrigin;
import akka.http.javadsl.model.headers.HttpOriginRanges;
import akka.http.javadsl.server.Route;

/**
 * Custom Akka Http directive enabling CORS for the wrapped Route.
 */
public final class CorsEnablingDirective {

    private static final List<HttpHeader> CORS_HEADERS = Arrays.asList(
            AccessControlAllowOrigin.create(HttpOriginRanges.ALL),
            AccessControlAllowMethods.create(HttpMethods.OPTIONS, HttpMethods.GET, HttpMethods.PUT,
                    HttpMethods.POST, HttpMethods.HEAD, HttpMethods.DELETE));

    private CorsEnablingDirective() {
        // no op
    }

    /**
     * Enables CORS - Cross-Origin Resource Sharing - for the wrapped {@code inner} Route.
     *
     * @param inner the inner route to be wrapped with the CORS enabling
     * @return the new route wrapping {@code inner} with the CORS enabling
     */
    public static Route enableCors(final Supplier<Route> inner) {

        return extractActorSystem(actorSystem -> {
            final boolean enableCors = actorSystem.settings().config().getBoolean(ConfigKeys.ENABLE_CORS);
            if (enableCors) {
                return route(
                        options(() -> complete(
                                HttpResponse.create().withStatus(StatusCodes.OK).addHeaders(CORS_HEADERS))),
                        respondWithHeaders(CORS_HEADERS, inner)
                );
            } else {
                return inner.get();
            }
        });
    }

}
