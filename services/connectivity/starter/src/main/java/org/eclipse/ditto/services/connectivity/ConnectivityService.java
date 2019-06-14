/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity;

import java.util.function.UnaryOperator;

import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.services.connectivity.actors.ConnectivityRootActor;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.ActorMaterializer;

/**
 * Entry point of the Connectivity service.
 * <ul>
 *     <li>Reads configuration, enhances it with cloud environment settings.</li>
 *     <li>Sets up Akka actor system.</li>
 * </ul>
 */
@AllParametersAndReturnValuesAreNonnullByDefault
public final class ConnectivityService extends DittoService<ConnectivityConfig> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectivityService.class);

    /**
     * Name for the Akka Actor System of the Connectivity service.
     */
    private static final String SERVICE_NAME = "connectivity";

    private ConnectivityService() {
        super(LOGGER, SERVICE_NAME, ConnectivityRootActor.ACTOR_NAME);
    }

    /**
     * Starts the Connectivity service.
     *
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        final ConnectivityService connectivityService = new ConnectivityService();
        connectivityService.start().getWhenTerminated().toCompletableFuture().join();
    }

    @Override
    protected ConnectivityConfig getServiceSpecificConfig(final ScopedConfig dittoConfig) {
        return DittoConnectivityConfig.of(dittoConfig);
    }

    @Override
    protected Props getMainRootActorProps(final ConnectivityConfig connectivityConfig, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return ConnectivityRootActor.props(connectivityConfig, pubSubMediator, materializer, UnaryOperator.identity());
    }

}
