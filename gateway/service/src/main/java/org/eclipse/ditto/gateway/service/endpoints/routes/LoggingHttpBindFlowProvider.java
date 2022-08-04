/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes;

import com.typesafe.config.Config;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;
import akka.stream.javadsl.Flow;

/**
 * Default HTTP bind flow provider, which logs the requests.
 *
 * @since 3.0.0
 */
public final class LoggingHttpBindFlowProvider implements HttpBindFlowProvider {

    private final ActorSystem actorSystem;

    /**
     * @param actorSystem the actor system in which to load the extension.
     * @param config the config the extension is configured.
     */
    @SuppressWarnings("unused")
    public LoggingHttpBindFlowProvider(final ActorSystem actorSystem, final Config config) {
        this.actorSystem = actorSystem;
    }

    @Override
    public Flow<HttpRequest, HttpResponse, NotUsed> getFlow(final Route innerRoute) {
        return Directives.logRequest("http", Logging.DebugLevel(), () -> innerRoute).flow(actorSystem);
    }
}
