/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.connectivity;

import org.eclipse.ditto.services.connectivity.actors.ConnectivityRootActor;
import org.eclipse.ditto.services.connectivity.util.ConfigKeys;
import org.eclipse.ditto.services.base.BaseConfigKey;
import org.eclipse.ditto.services.base.BaseConfigKeys;
import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

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
public final class ConnectivityService extends DittoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectivityService.class);

    /**
     * Name for the Akka Actor System of the Connectivity service.
     */
    private static final String SERVICE_NAME = "connectivity";

    private ConnectivityService() {
        super(LOGGER, SERVICE_NAME, ConnectivityRootActor.ACTOR_NAME, BaseConfigKeys.getBuilder()
                .put(BaseConfigKey.Cluster.MAJORITY_CHECK_ENABLED, ConfigKeys.Cluster.MAJORITY_CHECK_ENABLED)
                .put(BaseConfigKey.Cluster.MAJORITY_CHECK_DELAY, ConfigKeys.Cluster.MAJORITY_CHECK_DELAY)
                .build());
    }

    /**
     * Starts the Connectivity service.
     *
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        final ConnectivityService connectivityService = new ConnectivityService();
        connectivityService.start();
    }

    @Override
    protected Props getMainRootActorProps(final Config config, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return ConnectivityRootActor.props(config, pubSubMediator, materializer);
    }

}
