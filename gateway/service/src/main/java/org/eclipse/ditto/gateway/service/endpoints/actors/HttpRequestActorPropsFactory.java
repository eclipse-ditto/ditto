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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.concurrent.CompletableFuture;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.gateway.service.util.config.GatewayConfig;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;

import com.typesafe.config.Config;

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
     * @param gatewayConfig the configuration settings of the Gateway service.
     * @return Props of the actor.
     */
    Props props(ActorRef proxyActor,
            HeaderTranslator headerTranslator,
            HttpRequest httpRequest,
            CompletableFuture<HttpResponse> httpResponseFuture,
            GatewayConfig gatewayConfig);

    static HttpRequestActorPropsFactory get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }


    final class ExtensionId extends DittoExtensionPoint.ExtensionId<HttpRequestActorPropsFactory> {

        private static final String CONFIG_KEY = "http-request-actor-props-factory";

        private ExtensionId(final ExtensionIdConfig<HttpRequestActorPropsFactory> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<HttpRequestActorPropsFactory> computeConfig(final Config config) {
            return ExtensionIdConfig.of(HttpRequestActorPropsFactory.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
