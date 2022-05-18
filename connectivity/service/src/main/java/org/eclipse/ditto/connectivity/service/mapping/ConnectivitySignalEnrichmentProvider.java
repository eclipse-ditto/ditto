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

import java.util.Arrays;

import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.internal.models.signalenrichment.DefaultSignalEnrichmentConfig;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentConfig;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;

import akka.actor.AbstractExtensionId;
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
public abstract class ConnectivitySignalEnrichmentProvider implements DittoExtensionPoint {

    /**
     * Create a signal-enriching facade from the ID of a connection.
     *
     * @param connectionId the connection ID.
     * @return the facade.
     */
    public abstract SignalEnrichmentFacade getFacade(ConnectionId connectionId);

    /**
     * Load a {@code ConnectivitySignalEnrichmentProvider} dynamically according to the streaming configuration.
     *
     * @param actorSystem The actor system in which to load the facade provider class.
     * @return The configured facade provider.
     */
    public static ConnectivitySignalEnrichmentProvider get(final ActorSystem actorSystem) {
        return ExtensionId.INSTANCE.get(actorSystem);
    }

    /**
     * ID of the actor system extension to provide signal enrichment for connectivity.
     */
    private static final class ExtensionId extends AbstractExtensionId<ConnectivitySignalEnrichmentProvider> {

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
