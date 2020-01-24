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
package org.eclipse.ditto.services.gateway.endpoints.utils;

import java.util.Arrays;

import org.eclipse.ditto.services.base.config.DefaultSignalEnrichmentConfig;
import org.eclipse.ditto.services.base.config.SignalEnrichmentConfig;
import org.eclipse.ditto.services.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.services.utils.akka.AkkaClassLoader;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;
import akka.http.javadsl.model.HttpRequest;

/**
 * Provider of {@link SignalEnrichmentFacade} to be loaded by reflection.
 * Implementations MUST have a public constructor taking the following parameters as arguments:
 * <ul>
 * <li>ActorSystem actorSystem: actor system in which this provider is loaded,</li>
 * <li>Config config: configuration for the facade provider.</li>
 * </ul>
 */
public abstract class GatewaySignalEnrichmentProvider implements Extension {

    /**
     * Create a {@link SignalEnrichmentFacade} from the HTTP request that
     * created the websocket or SSE stream that requires it.
     *
     * @param request the HTTP request.
     * @return the signal-enriching facade.
     */
    public abstract SignalEnrichmentFacade getFacade(HttpRequest request);

    /**
     * Get the {@code GatewaySignalEnrichmentProvider} for the actor system.
     * The provider is created dynamically according to the streaming configuration.
     *
     * @param actorSystem The actor system in which to load the facade provider class.
     * @return The configured facade provider.
     */
    public static GatewaySignalEnrichmentProvider get(final ActorSystem actorSystem) {
        return ExtensionId.INSTANCE.get(actorSystem);
    }

    /**
     * ID of the actor system extension to provide signal enrichment for gateway.
     */
    private static final class ExtensionId extends AbstractExtensionId<GatewaySignalEnrichmentProvider> {

        private static final String SIGNAL_ENRICHMENT_CONFIG_PATH = "ditto.gateway.streaming";

        private static final ExtensionId INSTANCE = new ExtensionId();

        @Override
        public GatewaySignalEnrichmentProvider createExtension(final ExtendedActorSystem system) {
            final SignalEnrichmentConfig config =
                    DefaultSignalEnrichmentConfig.of(
                            system.settings().config().getConfig(SIGNAL_ENRICHMENT_CONFIG_PATH));
            return AkkaClassLoader.instantiate(system, GatewaySignalEnrichmentProvider.class,
                    config.getProvider(),
                    Arrays.asList(ActorSystem.class, SignalEnrichmentConfig.class),
                    Arrays.asList(system, config)
            );
        }
    }
}
