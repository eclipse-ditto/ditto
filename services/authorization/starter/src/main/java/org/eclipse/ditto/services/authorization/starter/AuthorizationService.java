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
import org.eclipse.ditto.services.authorization.starter.proxy.DefaultAuthorizationProxyPropsFactory;
import org.eclipse.ditto.services.authorization.util.config.AuthorizationConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.ActorMaterializer;

/**
 * The Authorization service for Eclipse Ditto.
 */
public final class AuthorizationService extends AbstractAuthorizationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationService.class);

    private AuthorizationService() {
        super(LOGGER);
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
    protected Props getMainRootActorProps(final AuthorizationConfigReader configReader, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return AuthorizationRootActor.props(configReader, pubSubMediator, new DefaultAuthorizationProxyPropsFactory());
    }

}
