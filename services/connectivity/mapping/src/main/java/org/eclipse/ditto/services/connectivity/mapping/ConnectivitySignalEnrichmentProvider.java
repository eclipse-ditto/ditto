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
package org.eclipse.ditto.services.connectivity.mapping;

import java.util.Arrays;

import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.services.base.config.DefaultSignalEnrichmentConfig;
import org.eclipse.ditto.services.base.config.SignalEnrichmentConfig;
import org.eclipse.ditto.services.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.services.utils.akka.AkkaClassLoader;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;

/**
 * Provider of {@link SignalEnrichmentFacade} to be loaded by reflection.
 * Implementations MUST have a public constructor taking the following parameters as arguments:
 * <ul>
 * <li>ActorSystem actorSystem: actor system in which this provider is loaded,</li>
 * <li>ActorRef commandHandler: recipient of retrieve-thing commands,</li>
 * <li>Config config: configuration for the facade provider.</li>
 * </ul>
 */
public interface ConnectivitySignalEnrichmentProvider extends Extension {

    /**
     * Create a signal-enriching facade from the ID of a connection.
     *
     * @param connectionId the connection ID.
     * @return the facade.
     */
    SignalEnrichmentFacade getFacade(ConnectionId connectionId);

    /**
     * Load a {@code ThingEnrichingFacadeProvider} dynamically according to the streaming configuration.
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
    final class ExtensionId extends AbstractExtensionId<ConnectivitySignalEnrichmentProvider> {

        private static final String SIGNAL_ENRICHMENT_CONFIG_PATH = "ditto.connectivity";

        private static final ExtensionId INSTANCE = new ExtensionId();

        @Override
        public ConnectivitySignalEnrichmentProvider createExtension(final ExtendedActorSystem system) {
            final SignalEnrichmentConfig signalEnrichmentConfig =
                    DefaultSignalEnrichmentConfig.of(
                            system.settings().config().getConfig(SIGNAL_ENRICHMENT_CONFIG_PATH));
            return AkkaClassLoader.instantiate(system, ConnectivitySignalEnrichmentProvider.class,
                    signalEnrichmentConfig.getProvider(),
                    Arrays.asList(ActorSystem.class, SignalEnrichmentConfig.class),
                    Arrays.asList(system, signalEnrichmentConfig)
            );
        }
    }
}
