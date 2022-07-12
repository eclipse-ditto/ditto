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
package org.eclipse.ditto.gateway.service.endpoints.actors;

import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.gateway.service.util.config.endpoints.CommandConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.HttpConfig;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;

/**
 * Factory of props of actors that handle HTTP requests.
 */
@FunctionalInterface
public interface HttpRequestActorPropsFactory extends DittoExtensionPoint {

    /**
     * Create Props object of an actor to handle 1 HTTP request.
     *
     * @param proxyActor proxy actor to forward all commands.
     * @param headerTranslator translator of Ditto headers.
     * @param httpRequest the HTTP request.
     * @param httpResponseFuture promise of an HTTP response to be fulfilled by the actor.
     * @param httpConfig the configuration settings of the Gateway service's HTTP endpoint.
     * @param commandConfig the configuration settings for incoming commands (via HTTP requests) in the gateway.
     * @return Props of the actor.
     */
    Props props(ActorRef proxyActor,
            HeaderTranslator headerTranslator,
            HttpRequest httpRequest,
            CompletableFuture<HttpResponse> httpResponseFuture,
            HttpConfig httpConfig,
            CommandConfig commandConfig);

    static HttpRequestActorPropsFactory get(final ActorSystem actorSystem) {
        return ExtensionId.INSTANCE.get(actorSystem);
    }


    final class ExtensionId extends DittoExtensionPoint.ExtensionId<HttpRequestActorPropsFactory> {

        private static final String CONFIG_PATH = "ditto.gateway.http.actor-props-factory";
        private static final ExtensionId INSTANCE = new ExtensionId(HttpRequestActorPropsFactory.class);

        /**
         * Returns the {@code ExtensionId} for the implementation that should be loaded.
         *
         * @param parentClass the class of the extensions for which an implementation should be loaded.
         */
        private ExtensionId(final Class<HttpRequestActorPropsFactory> parentClass) {
            super(parentClass);
        }

        @Override
        protected String getConfigPath() {
            return CONFIG_PATH;
        }

    }

}
