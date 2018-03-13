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

import org.eclipse.ditto.services.authorization.util.cache.AuthorizationCache;
import org.eclipse.ditto.services.authorization.util.config.AuthorizationConfigReader;

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

    private final AuthorizationCache cache;

    private AuthorizationRootActor(final AuthorizationConfigReader configReader, final ActorRef pubSubMediator) {
        this.cache = new AuthorizationCache(configReader.getCacheConfigReader());
        // create shard region as children so that if this actor dies all reference to the cache is gone
    }

    public static Props props(final AuthorizationConfigReader configReader, final ActorRef pubSubMediator) {
        return Props.create(AuthorizationRootActor.class, new AuthorizationRootActor(configReader, pubSubMediator));
    }

    @Override
    public Receive createReceive() {
        // TODO: do something.
        return ReceiveBuilder.create().build();
    }
}
