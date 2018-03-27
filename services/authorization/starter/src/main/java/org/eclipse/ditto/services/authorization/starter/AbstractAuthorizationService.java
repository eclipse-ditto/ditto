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

import org.eclipse.ditto.services.authorization.starter.actors.AuthorizationRootActor;
import org.eclipse.ditto.services.authorization.util.config.AuthorizationConfigReader;
import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.services.base.metrics.StatsdMetricsStarter;
import org.slf4j.Logger;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Abstract base implementation for starting an Authorization service with configurable actors.
 */
public abstract class AbstractAuthorizationService extends DittoService<AuthorizationConfigReader> {

    /**
     * Name for the Akka actor system of the Authorization service.
     */
    static final String SERVICE_NAME = "authorization";

    protected AbstractAuthorizationService(final Logger logger) {
        super(logger, SERVICE_NAME, AuthorizationRootActor.ACTOR_NAME, AuthorizationConfigReader.from(SERVICE_NAME));
    }

    @Override
    protected void startDevOpsCommandsActor(final ActorSystem actorSystem, final Config config) {
        // TODO: start DevOpsCommandsActor
    }

    @Override
    protected void startStatsdMetricsReporter(final ActorSystem actorSystem,
            final AuthorizationConfigReader configReader) {
        StatsdMetricsStarter.newInstance(configReader, actorSystem, SERVICE_NAME).run();
    }

}
