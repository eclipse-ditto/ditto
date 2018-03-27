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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.services.authorization.util.EntityRegionMap;
import org.eclipse.ditto.services.authorization.util.cache.AuthorizationCaches;
import org.eclipse.ditto.services.authorization.util.cache.EnforcerCacheLoader;
import org.eclipse.ditto.services.authorization.util.cache.ThingEnforcementIdCacheLoader;
import org.eclipse.ditto.services.authorization.util.cache.entry.Entry;
import org.eclipse.ditto.services.authorization.util.config.AuthorizationConfigReader;
import org.eclipse.ditto.services.authorization.util.enforcement.EnforcerActorFactory;
import org.eclipse.ditto.services.authorization.util.update.CacheUpdaterPropsFactory;
import org.eclipse.ditto.services.base.metrics.StatsdMetricsReporter;
import org.eclipse.ditto.services.models.authorization.EntityId;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Ditto default implementation of {@link AuthorizationProxyPropsFactory}.
 */
public final class DefaultAuthorizationProxyPropsFactory implements AuthorizationProxyPropsFactory {

    @Override
    public Props props(final ActorContext context, final AuthorizationConfigReader configReader,
            final ActorRef pubSubMediator, final ActorRef policiesShardRegionProxy,
            final ActorRef thingsShardRegionProxy) {
        final EntityRegionMap entityRegionMap =  EntityRegionMap.newBuilder()
                .put(PolicyCommand.RESOURCE_TYPE, policiesShardRegionProxy)
                .put(ThingCommand.RESOURCE_TYPE, thingsShardRegionProxy)
                .build();

        final Duration askTimeout = configReader.caches().askTimeout();
        final Map<String, AsyncCacheLoader<EntityId, Entry<EntityId>>> enforcementIdCacheLoaders = new HashMap<>();
        final ThingEnforcementIdCacheLoader thingEnforcerIdCacheLoader =
                new ThingEnforcementIdCacheLoader(askTimeout, thingsShardRegionProxy);
        enforcementIdCacheLoaders.put(ThingCommand.RESOURCE_TYPE, thingEnforcerIdCacheLoader);

        final EnforcerCacheLoader enforcerCacheLoader =
                new EnforcerCacheLoader(askTimeout, thingsShardRegionProxy, policiesShardRegionProxy);

        final AuthorizationCaches caches = new AuthorizationCaches(configReader.caches(), enforcerCacheLoader,
                enforcementIdCacheLoaders,
                namedMetricRegistry -> StatsdMetricsReporter.getInstance().add(namedMetricRegistry));

        final Props enforcerProps = EnforcerActorFactory.props(entityRegionMap, caches);

        // start cache updater
        final Props cacheUpdaterProps = CacheUpdaterPropsFactory.props(pubSubMediator, caches, configReader.instanceIndex());
        context.actorOf(cacheUpdaterProps, CacheUpdaterPropsFactory.ACTOR_NAME);
        return enforcerProps;
    }
}
