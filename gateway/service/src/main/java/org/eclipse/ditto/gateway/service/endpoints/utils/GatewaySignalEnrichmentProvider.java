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

import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.gateway.service.util.config.streaming.GatewaySignalEnrichmentConfig;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

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

    String CONFIG_PATH = "ditto.gateway.streaming.signal-enrichment.signal-enrichment-provider";

    /**
     * Create a {@link SignalEnrichmentFacade} from the HTTP request that
     * created the websocket or SSE stream that requires it.
     *
     * @param request the HTTP request.
     * @return the signal-enriching facade.
     */
    CompletionStage<SignalEnrichmentFacade> getFacade(HttpRequest request);

    default GatewaySignalEnrichmentConfig getSignalEnrichmentConfig(final ActorSystem actorSystem) {
        return DittoGatewayConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()))
                .getStreamingConfig()
                .getSignalEnrichmentConfig();
    }

    /**
     * Loads the implementation of {@code GatewaySignalEnrichmentProvider} which is configured for the {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code GatewaySignalEnrichmentProvider} should be loaded.
     * @return the {@code GatewaySignalEnrichmentProvider} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @since 3.0.0
     */
    static GatewaySignalEnrichmentProvider get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        final var implementation = actorSystem.settings().config().getString(CONFIG_PATH);

        return new ExtensionId<>(implementation, GatewaySignalEnrichmentProvider.class).get(actorSystem);
    }

}
