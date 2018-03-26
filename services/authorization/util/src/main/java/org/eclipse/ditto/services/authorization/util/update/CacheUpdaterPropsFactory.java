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

import static java.util.Objects.requireNonNull;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.ditto.services.authorization.util.cache.AuthorizationCaches;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Factory which creates an updater for the authorization cache.
 */
public final class CacheUpdaterPropsFactory {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "cacheUpdater";

    private CacheUpdaterPropsFactory() {
        throw new AssertionError();
    }

    /**
     * Create an Akka {@code Props} object for an cache updater actor.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param caches authorization caches.
     * @param instanceIndex the index of this service instance.
     * @return Akka {@code Props} object.
     */
    public static Props props(final ActorRef pubSubMediator, final AuthorizationCaches caches,
            final int instanceIndex) {
        requireNonNull(pubSubMediator);
        requireNonNull(caches);

        final Set<CacheUpdateStrategy> cacheUpdateStrategies = new HashSet<>();
        final ThingCacheUpdateStrategy thingCacheUpdateStrategy = new ThingCacheUpdateStrategy(caches);
        cacheUpdateStrategies.add(thingCacheUpdateStrategy);
        final PolicyCacheUpdateStrategy policyCacheUpdateStrategy = new PolicyCacheUpdateStrategy(caches);
        cacheUpdateStrategies.add(policyCacheUpdateStrategy);

        return Props.create(CacheUpdater.class,
                () -> new CacheUpdater(pubSubMediator, cacheUpdateStrategies, instanceIndex));
    }
}
