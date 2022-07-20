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
package org.eclipse.ditto.things.service.starter;

import org.eclipse.ditto.base.service.DittoService;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.things.service.common.config.DittoThingsConfig;
import org.eclipse.ditto.things.service.common.config.ThingsConfig;
import org.eclipse.ditto.things.service.persistence.actors.ThingPersistenceActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorRef;
import akka.actor.Props;

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
    protected Props getMainRootActorProps(final ThingsConfig thingsConfig, final ActorRef pubSubMediator) {
        return ThingsRootActor.props(thingsConfig, pubSubMediator, ThingPersistenceActor::props);
    }

    @Override
    protected Config appendAkkaPersistenceMongoUriToRawConfig() {
        final var mongoDbConfig = serviceSpecificConfig.getMongoDbConfig();
        final String mongoDbUri = mongoDbConfig.getMongoDbUri();
        return rawConfig.withValue(MONGO_URI_CONFIG_PATH, ConfigValueFactory.fromAnyRef(mongoDbUri));
    }

}
