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

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.extractActorSystem;
import static akka.http.javadsl.server.Directives.options;
import static akka.http.javadsl.server.Directives.respondWithHeaders;
import static akka.http.javadsl.server.Directives.route;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.services.gateway.util.config.endpoints.HttpConfig;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.AccessControlAllowHeaders;
import akka.http.javadsl.model.headers.AccessControlAllowMethods;
import akka.http.javadsl.model.headers.AccessControlAllowOrigin;
import akka.http.javadsl.model.headers.AccessControlRequestHeaders;
import akka.http.javadsl.model.headers.HttpOriginRanges;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;

/**
 * Custom Akka Http directive enabling CORS for the wrapped Route.
 */
public final class CorsEnablingDirective {

    private static final List<HttpHeader> CORS_HEADERS = Arrays.asList(
            AccessControlAllowOrigin.create(HttpOriginRanges.ALL),
            AccessControlAllowMethods.create(HttpMethods.OPTIONS, HttpMethods.GET, HttpMethods.PUT,
                    HttpMethods.POST, HttpMethods.HEAD, HttpMethods.DELETE));

    private final HttpConfig httpConfig;

    private CorsEnablingDirective(final HttpConfig httpConfig) {
        this.httpConfig = checkNotNull(httpConfig, "HTTP config");
    }

    /**
     * Returns an instance of {@code CorsEnablingDirective}.
     *
     * @param httpConfig the configuration settings of the Gateway service's HTTP behaviour.
     * @return the instance.
     * @throws NullPointerException if {@code httpConfig} is {@code null}.
     */
    public static CorsEnablingDirective getInstance(final HttpConfig httpConfig) {
        return new CorsEnablingDirective(httpConfig);
    }

    /**
     * Enables CORS - Cross-Origin Resource Sharing - for the wrapped {@code inner} Route.
     *
     * @param inner the inner route to be wrapped with the CORS enabling.
     * @return the new route wrapping {@code inner} with the CORS enabling.
     */
    public Route enableCors(final Supplier<Route> inner) {
        return extractActorSystem(actorSystem -> {
            if (!httpConfig.isEnableCors()) {
                return inner.get();
            }
            return Directives.optionalHeaderValueByType(AccessControlRequestHeaders.class, corsRequestHeaders -> {
                final ArrayList<HttpHeader> newHeaders = new ArrayList<>(CORS_HEADERS);
                corsRequestHeaders.ifPresent(toAdd ->
                        newHeaders.add(AccessControlAllowHeaders.create(
                                StreamSupport.stream(toAdd.getHeaders().spliterator(), false).toArray(String[]::new))
                        )
                );
                return route(options(() -> complete(
                        HttpResponse.create().withStatus(StatusCodes.OK).addHeaders(newHeaders))),
                        respondWithHeaders(newHeaders, inner)
                );
            });
        });
    }

}
