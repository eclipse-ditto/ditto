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
package org.eclipse.ditto.services.gateway.endpoints.actors;

import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.endpoints.config.HttpConfig;
import org.eclipse.ditto.signals.commands.base.Command;

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
            final HttpConfig httpConfig) {

        super(proxyActor, headerTranslator, request, httpResponseFuture, httpConfig);
    }

    /**
     * Creates the Akka configuration object for this {@code HttpRequestActor} for the given {@code proxyActor}, {@code
     * request}, and {@code httpResponseFuture} which will be completed with a {@link HttpResponse}.
     *
     * @param proxyActor the proxy actor which delegates commands.
     * @param headerTranslator the {@link org.eclipse.ditto.protocoladapter.HeaderTranslator} used to map ditto headers
     * to (external) Http headers.
     * @param request the HTTP request
     * @param httpResponseFuture the completable future which is completed with a HTTP response.
     * @param httpConfig the configuration settings of the Gateway service's HTTP endpoint.
     * @return the configuration object.
     */
    public static Props props(final ActorRef proxyActor,
            final HeaderTranslator headerTranslator,
            final HttpRequest request,
            final CompletableFuture<HttpResponse> httpResponseFuture,
            final HttpConfig httpConfig) {

        return Props.create(HttpRequestActor.class, proxyActor, headerTranslator, request, httpResponseFuture,
                httpConfig);
    }

}
