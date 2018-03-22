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
package org.eclipse.ditto.services.authorization.util.update;

import org.eclipse.ditto.services.authorization.util.cache.AuthorizationCaches;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Listens to events, updates authorization cache.
 */
public final class CacheUpdater extends AbstractCacheUpdater {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "cacheUpdater";

    private CacheUpdater(final ActorRef pubSubMediator,
            final AuthorizationCaches caches, final int instanceIndex) {
        super(pubSubMediator, caches, instanceIndex);
    }

    /**
     * Create Akka {@code Props} object for this actor.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param caches authorization caches.
     * @return Akka {@code Props} object for this actor.
     */
    public static Props props(final ActorRef pubSubMediator, final AuthorizationCaches caches,
            final int instanceIndex) {
        return Props.create(CacheUpdater.class, () -> new CacheUpdater(pubSubMediator, caches, instanceIndex));
    }
}
