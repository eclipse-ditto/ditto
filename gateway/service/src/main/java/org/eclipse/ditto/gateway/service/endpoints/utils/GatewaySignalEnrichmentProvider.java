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
package org.eclipse.ditto.gateway.service.endpoints.utils;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;


import com.typesafe.config.Config;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;

/**
 * Provider of {@link SignalEnrichmentFacade} to be loaded by reflection.
 * Implementations MUST have a public constructor taking the following parameters as arguments:
 * <ul>
 * <li>ActorSystem actorSystem: actor system in which this provider is loaded,</li>
 * <li>Config config: configuration for the facade provider.</li>
 * </ul>
 */
public interface GatewaySignalEnrichmentProvider extends DittoExtensionPoint {

    /**
     * Create a {@link SignalEnrichmentFacade} from the HTTP request that
     * created the websocket or SSE stream that requires it.
     *
     * @param request the HTTP request.
     * @return the signal-enriching facade.
     */
    CompletionStage<SignalEnrichmentFacade> getFacade(HttpRequest request);

    /**
     * Loads the implementation of {@code GatewaySignalEnrichmentProvider} which is configured for the {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code GatewaySignalEnrichmentProvider} should be loaded.
     * @param config the configuration of this extension.
     * @return the {@code GatewaySignalEnrichmentProvider} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @since 3.0.0
     */
    static GatewaySignalEnrichmentProvider get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<GatewaySignalEnrichmentProvider> {

        private static final String CONFIG_KEY = "signal-enrichment-provider";

        private ExtensionId(final ExtensionIdConfig<GatewaySignalEnrichmentProvider> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<GatewaySignalEnrichmentProvider> computeConfig(final Config config) {
            return ExtensionIdConfig.of(GatewaySignalEnrichmentProvider.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
