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

import java.util.List;

import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.internal.models.signalenrichment.DefaultSignalEnrichmentConfig;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentConfig;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;

import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;

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
     * @return The configured facade provider.
     */
    static ConnectivitySignalEnrichmentProvider get(final ActorSystem actorSystem) {
        return ExtensionId.INSTANCE.get(actorSystem);
    }

    /**
     * ID of the actor system extension to provide signal enrichment for connectivity.
     */
    final class ExtensionId extends DittoExtensionPoint.ExtensionId<ConnectivitySignalEnrichmentProvider> {

        private static final String SIGNAL_ENRICHMENT_CONFIG_PATH = "ditto.connectivity";
        private static final String CONFIG_PATH = "ditto.connectivity.signal-enrichment.provider";

        private static final ExtensionId INSTANCE = new ExtensionId(ConnectivitySignalEnrichmentProvider.class);

        /**
         * Returns the {@code ExtensionId} for the implementation that should be loaded.
         *
         * @param parentClass the class of the extensions for which an implementation should be loaded.
         */
        public ExtensionId(final Class<ConnectivitySignalEnrichmentProvider> parentClass) {
            super(parentClass);
        }

        @Override
        public ConnectivitySignalEnrichmentProvider createExtension(final ExtendedActorSystem system) {
            final SignalEnrichmentConfig signalEnrichmentConfig =
                    DefaultSignalEnrichmentConfig.of(
                            system.settings().config().getConfig(SIGNAL_ENRICHMENT_CONFIG_PATH));

            return AkkaClassLoader.instantiate(system, ConnectivitySignalEnrichmentProvider.class,
                    getImplementation(system),
                    List.of(ActorSystem.class, SignalEnrichmentConfig.class),
                    List.of(system, signalEnrichmentConfig));
        }

        protected String getImplementation(final ExtendedActorSystem actorSystem) {
            return actorSystem.settings().config().getString(getConfigPath());
        }

        @Override
        protected String getConfigPath() {
            return CONFIG_PATH;
        }

    }

}
