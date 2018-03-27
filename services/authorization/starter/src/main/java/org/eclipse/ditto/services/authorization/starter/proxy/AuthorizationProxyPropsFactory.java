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
package org.eclipse.ditto.services.authorization.starter.proxy;

import org.eclipse.ditto.services.authorization.util.config.AuthorizationConfigReader;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Defines the {@link Props} which are used to create an Authorization Proxy Actor for an entity.
 */
public interface AuthorizationProxyPropsFactory {

    Props props(final ActorContext context, final AuthorizationConfigReader configReader,
            final ActorRef pubSubMediator, final ActorRef policiesShardRegionProxy,
            final ActorRef thingsShardRegionProxy);
    

}
