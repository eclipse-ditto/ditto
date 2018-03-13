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

import com.typesafe.config.Config;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;

/**
 * The root actor of Authorization Service.
 */
public final class AuthorizationRootActor extends AbstractActor {

    // TODO: supervisory strategy, best without code duplication

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "authorizationRoot";

    private AuthorizationRootActor(final Config config, final ActorRef pubSubMediator) {

    }

    public static final Props props(final Config config, final ActorRef pubSubMediator) {
        return Props.create(AuthorizationRootActor.class, new AuthorizationRootActor(config, pubSubMediator));
    }

    @Override
    public Receive createReceive() {
        // TODO: do something.
        return ReceiveBuilder.create().build();
    }
}
