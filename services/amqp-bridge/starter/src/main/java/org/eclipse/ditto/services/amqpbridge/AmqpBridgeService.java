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
package org.eclipse.ditto.services.amqpbridge;

import org.eclipse.ditto.services.amqpbridge.actors.AmqpBridgeRootActor;
import org.eclipse.ditto.services.amqpbridge.util.ConfigKeys;
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
 * Entry point of the AMQP Bridge.
 * <ul>
 *     <li>Reads configuration, enhances it with cloud environment settings.</li>
 *     <li>Sets up Akka actor system.</li>
 * </ul>
 */
@AllParametersAndReturnValuesAreNonnullByDefault
public final class AmqpBridgeService extends DittoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AmqpBridgeService.class);

    /**
     * Name for the Akka Actor System of the AMQP Bridge.
     */
    private static final String SERVICE_NAME = "amqp-bridge";

    private AmqpBridgeService() {
        super(LOGGER, SERVICE_NAME, AmqpBridgeRootActor.ACTOR_NAME, BaseConfigKeys.getBuilder()
                .put(BaseConfigKey.Cluster.MAJORITY_CHECK_ENABLED, ConfigKeys.Cluster.MAJORITY_CHECK_ENABLED)
                .put(BaseConfigKey.Cluster.MAJORITY_CHECK_DELAY, ConfigKeys.Cluster.MAJORITY_CHECK_DELAY)
                .build());
    }

    /**
     * Starts the AMQP 1.0 Bridge.
     *
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        final AmqpBridgeService amqpBridgeService = new AmqpBridgeService();
        amqpBridgeService.start();
    }

    @Override
    protected Props getMainRootActorProps(final Config config, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return AmqpBridgeRootActor.props(config, pubSubMediator, materializer);
    }

}
