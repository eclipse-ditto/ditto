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
package org.eclipse.ditto.services.things.starter;

import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.services.things.persistence.snapshotting.DittoThingSnapshotter;
import org.eclipse.ditto.services.things.starter.config.DittoThingsConfig;
import org.eclipse.ditto.services.things.starter.config.ThingsConfig;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.ActorMaterializer;

/**
 * Entry point of the Things Service.
 * <ul>
 * <li>Reads configuration, enhances it with cloud environment settings</li>
 * <li>Sets up ActorSystem</li>
 * <li>Wires up Akka HTTP Routes</li>
 * </ul>
 */
public final class ThingsService extends DittoService<ThingsConfig> {

    /**
     * Name of the Things service.
     */
    public static final String SERVICE_NAME = "things";

    private static final Logger LOGGER = LoggerFactory.getLogger(ThingsService.class);

    private ThingsService() {
        super(LOGGER, SERVICE_NAME, ThingsRootActor.ACTOR_NAME);
    }

    /**
     * Starts the Things service.
     *
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        final ThingsService thingsService = new ThingsService();
        thingsService.start().getWhenTerminated().toCompletableFuture().join();
    }

    @Override
    protected ThingsConfig getServiceSpecificConfig(final ScopedConfig dittoConfig) {
        return DittoThingsConfig.of(dittoConfig);
    }

    @Override
    protected Props getMainRootActorProps(final ThingsConfig thingsConfig, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return ThingsRootActor.props(thingsConfig, pubSubMediator, materializer, DittoThingSnapshotter::getInstance);
    }

}
