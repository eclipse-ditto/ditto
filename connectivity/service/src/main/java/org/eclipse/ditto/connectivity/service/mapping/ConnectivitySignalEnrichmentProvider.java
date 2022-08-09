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
package org.eclipse.ditto.connectivity.service.mapping;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Provider of {@link SignalEnrichmentFacade} to be loaded by reflection.
 * Implementations MUST have a public constructor taking the following parameters as arguments:
 * <ul>
 * <li>ActorSystem actorSystem: actor system in which this provider is loaded,</li>
 * <li>ActorRef commandHandler: recipient of retrieve-thing commands,</li>
 * <li>Config config: configuration for the facade provider.</li>
 * </ul>
 */
public interface ConnectivitySignalEnrichmentProvider extends DittoExtensionPoint {

    /**
     * Create a signal-enriching facade from the ID of a connection.
     *
     * @param connectionId the connection ID.
     * @return the facade.
     */
    SignalEnrichmentFacade getFacade(ConnectionId connectionId);

    /**
     * Load a {@code ConnectivitySignalEnrichmentProvider} dynamically according to the streaming configuration.
     *
     * @param actorSystem The actor system in which to load the facade provider class.
     * @param config the config the extension is configured.
     * @return The configured facade provider.
     */
    static ConnectivitySignalEnrichmentProvider get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    /**
     * ID of the actor system extension to provide signal enrichment for connectivity.
     */
    final class ExtensionId extends DittoExtensionPoint.ExtensionId<ConnectivitySignalEnrichmentProvider> {

        private static final String CONFIG_KEY = "signal-enrichment-provider";

        private ExtensionId(final ExtensionIdConfig<ConnectivitySignalEnrichmentProvider> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<ConnectivitySignalEnrichmentProvider> computeConfig(final Config config) {
            return ExtensionIdConfig.of(ConnectivitySignalEnrichmentProvider.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
