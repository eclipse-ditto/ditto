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
package org.eclipse.ditto.gateway.service.endpoints.actors;

import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.gateway.service.util.config.endpoints.CommandConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.HttpConfig;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;

/**
 * Every HTTP Request causes one new Actor instance of this one to be created.
 * It holds the original sender of an issued {@link Command} and tells this one the completed HttpResponse.
 */
public final class HttpRequestActor extends AbstractHttpRequestActor {

    @SuppressWarnings("unused")
    private HttpRequestActor(final ActorRef proxyActor,
            final HeaderTranslator headerTranslator,
            final HttpRequest request,
            final CompletableFuture<HttpResponse> httpResponseFuture,
            final HttpConfig httpConfig,
            final CommandConfig commandConfig) {

        super(proxyActor,
                headerTranslator,
                request,
                httpResponseFuture,
                httpConfig,
                commandConfig);
    }

    /**
     * Creates the Akka configuration object for this {@code HttpRequestActor} for the given {@code proxyActor}, {@code
     * request}, and {@code httpResponseFuture} which will be completed with a {@link HttpResponse}.
     *
     * @param proxyActor the proxy actor which delegates commands.
     * @param headerTranslator the {@link org.eclipse.ditto.base.model.headers.translator.HeaderTranslator} used to map ditto headers
     * to (external) Http headers.
     * @param request the HTTP request
     * @param httpResponseFuture the completable future which is completed with a HTTP response.
     * @param httpConfig the configuration settings of the Gateway service's HTTP endpoint.
     * @param commandConfig the configuration settings for incoming commands (via HTTP requests) in the gateway.
     * @return the configuration object.
     */
    public static Props props(final ActorRef proxyActor,
            final HeaderTranslator headerTranslator,
            final HttpRequest request,
            final CompletableFuture<HttpResponse> httpResponseFuture,
            final HttpConfig httpConfig,
            final CommandConfig commandConfig) {

        return Props.create(HttpRequestActor.class,
                proxyActor,
                headerTranslator,
                request,
                httpResponseFuture,
                httpConfig,
                commandConfig);
    }

}
