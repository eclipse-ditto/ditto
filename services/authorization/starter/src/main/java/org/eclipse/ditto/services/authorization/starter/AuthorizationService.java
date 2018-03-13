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
package org.eclipse.ditto.services.authorization.starter;

import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.services.base.StatsdMongoDbMetricsStarter;
import org.eclipse.ditto.services.base.config.DittoServiceConfigReader;
import org.eclipse.ditto.services.base.config.ServiceConfigReader;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.ActorMaterializer;

/**
 * The Authorization service for Eclipse Ditto.
 */
@AllParametersAndReturnValuesAreNonnullByDefault
public final class AuthorizationService extends DittoService {

    /**
     * Name for the Akka actor system of the Authorization service.
     */
    static final String SERVICE_NAME = "authorization";

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationService.class);

    private AuthorizationService() {
        // TODO: use own config reader
        super(LOGGER, SERVICE_NAME, AuthorizationRootActor.ACTOR_NAME, DittoServiceConfigReader.from(SERVICE_NAME));
    }

    /**
     * Starts the Authorization service.
     *
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        final AuthorizationService authorizationService = new AuthorizationService();
        authorizationService.start();
    }

    @Override
    protected void startDevOpsCommandsActor(final ActorSystem actorSystem, final Config config) {
        // TODO: start DevOpsCommandsActor
    }

    @Override
    protected void startStatsdMetricsReporter(final ActorSystem actorSystem, final ServiceConfigReader configReader) {
        StatsdMongoDbMetricsStarter.newInstance(configReader, actorSystem, SERVICE_NAME, LOGGER).run();
    }

    @Override
    protected Props getMainRootActorProps(final ServiceConfigReader configReader, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return AuthorizationRootActor.props(configReader.getConfig(), pubSubMediator);
    }

}
