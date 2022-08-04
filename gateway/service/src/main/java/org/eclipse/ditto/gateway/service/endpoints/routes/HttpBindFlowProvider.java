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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;


import com.typesafe.config.Config;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Route;
import akka.stream.javadsl.Flow;

/**
 * Extension to add a custom bind flow for HTTP requests.
 *
 * @since 3.0.0
 */
public interface HttpBindFlowProvider extends DittoExtensionPoint {

    /**
     * Create a bind flow for HTTP requests.
     *
     * @return flow which processes HTTP requests.
     */
    Flow<HttpRequest, HttpResponse, NotUsed> getFlow(final Route innerRoute);

    /**
     * Loads the implementation of {@code HttpBindFlowProvider} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code HttpBindFlowProvider} should be loaded.
     * @param config the configuration for this extension.
     * @return the {@code HttpBindFlowProvider} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static HttpBindFlowProvider get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<HttpBindFlowProvider> {

        private static final String CONFIG_KEY = "http-bind-flow-provider";

        private ExtensionId(final ExtensionIdConfig<HttpBindFlowProvider> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<HttpBindFlowProvider> computeConfig(final Config config) {
            return ExtensionIdConfig.of(HttpBindFlowProvider.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
