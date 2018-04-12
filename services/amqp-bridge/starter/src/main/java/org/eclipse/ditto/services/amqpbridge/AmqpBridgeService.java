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
import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.services.base.config.DittoServiceConfigReader;
import org.eclipse.ditto.services.base.config.ServiceConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.ActorMaterializer;

/**
 * Entry point of the AMQP Bridge.
 * <ul>
 * <li>Reads configuration, enhances it with cloud environment settings.</li>
 * <li>Sets up Akka actor system.</li>
 * </ul>
 */
public final class AmqpBridgeService extends DittoService<ServiceConfigReader> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AmqpBridgeService.class);

    /**
     * Name for the Akka Actor System of the AMQP Bridge.
     */
    private static final String SERVICE_NAME = "amqp-bridge";

    private AmqpBridgeService() {
        super(LOGGER, SERVICE_NAME, AmqpBridgeRootActor.ACTOR_NAME, DittoServiceConfigReader.from(SERVICE_NAME));
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
    protected Props getMainRootActorProps(final ServiceConfigReader configReader, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return AmqpBridgeRootActor.props(configReader, pubSubMediator, materializer);
    }

}
