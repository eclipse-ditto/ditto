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
package org.eclipse.ditto.connectivity.service;

import org.eclipse.ditto.base.service.DittoService;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorRef;
import akka.actor.Props;

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
    public static final String SERVICE_NAME = "connectivity";

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
    protected Props getMainRootActorProps(final ConnectivityConfig connectivityConfig, final ActorRef pubSubMediator) {
        return ConnectivityRootActor.props(connectivityConfig, pubSubMediator);
    }

    @Override
    protected Config appendAkkaPersistenceMongoUriToRawConfig() {
        final var mongoDbConfig = serviceSpecificConfig.getMongoDbConfig();
        final String mongoDbUri = mongoDbConfig.getMongoDbUri();
        return rawConfig.withValue(MONGO_URI_CONFIG_PATH, ConfigValueFactory.fromAnyRef(mongoDbUri));
    }

}
