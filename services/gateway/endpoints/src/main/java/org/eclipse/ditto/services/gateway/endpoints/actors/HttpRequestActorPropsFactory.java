/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;

/**
 * Factory of props of actors that handle HTTP requests.
 */
@FunctionalInterface
public interface HttpRequestActorPropsFactory {

    /**
     * Create Props object of an actor to handle 1 HTTP request.
     *
     * @param proxyActor proxy actor to forward all commands.
     * @param headerTranslator translator of Ditto headers.
     * @param httpRequest the HTTP request.
     * @param httpResponseFuture promise of an HTTP response to be fulfilled by the actor.
     * @return Props of the actor.
     */
    Props props(ActorRef proxyActor, HeaderTranslator headerTranslator, HttpRequest httpRequest,
            CompletableFuture<HttpResponse> httpResponseFuture);
}
