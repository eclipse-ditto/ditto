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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.authorization.util.cache.CacheFactory;
import org.eclipse.ditto.services.authorization.util.cache.EnforcerCacheLoader;
import org.eclipse.ditto.services.authorization.util.cache.IdentityCache;
import org.eclipse.ditto.services.authorization.util.cache.ThingEnforcementIdCacheLoader;
import org.eclipse.ditto.services.authorization.util.cache.entry.Entry;
import org.eclipse.ditto.services.authorization.util.config.AuthorizationConfigReader;
import org.eclipse.ditto.services.authorization.util.enforcement.EnforcementProvider;
import org.eclipse.ditto.services.authorization.util.enforcement.EnforcerActor;
import org.eclipse.ditto.services.authorization.util.enforcement.LiveSignalEnforcement;
import org.eclipse.ditto.services.authorization.util.enforcement.MessageCommandEnforcement;
import org.eclipse.ditto.services.authorization.util.enforcement.PolicyCommandEnforcement;
import org.eclipse.ditto.services.authorization.util.enforcement.ThingCommandEnforcement;
import org.eclipse.ditto.services.authorization.util.update.PolicyCacheUpdateActor;
import org.eclipse.ditto.services.authorization.util.update.ThingCacheUpdateActor;
import org.eclipse.ditto.services.base.metrics.StatsdMetricsReporter;
import org.eclipse.ditto.services.models.authorization.EntityId;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import com.codahale.metrics.MetricRegistry;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Ditto default implementation of {@link AuthorizationProxyPropsFactory}.
 */
public final class DefaultAuthorizationProxyPropsFactory implements AuthorizationProxyPropsFactory {

    private static final String ENFORCER_CACHE_METRIC_NAME = "ditto.authorization.enforcer.cache";
    private static final String ID_CACHE_METRIC_NAME_PREFIX = "ditto.authorization.id.cache.";

    @Override
    public Props props(final ActorContext context, final AuthorizationConfigReader configReader,
            final ActorRef pubSubMediator, final ActorRef policiesShardRegionProxy,
            final ActorRef thingsShardRegionProxy) {
        final Consumer<Map.Entry<String, MetricRegistry>> metricsReportingConsumer =
                namedMetricRegistry -> StatsdMetricsReporter.getInstance().add(namedMetricRegistry);
        final Duration askTimeout = configReader.caches().askTimeout();

        final ThingEnforcementIdCacheLoader thingEnforcerIdCacheLoader =
                new ThingEnforcementIdCacheLoader(askTimeout, thingsShardRegionProxy);
        final Cache<EntityId, Entry<EntityId>> thingIdCache =
                CacheFactory.createCache(thingEnforcerIdCacheLoader, configReader.caches().id(),
                        ID_CACHE_METRIC_NAME_PREFIX + ThingCommand.RESOURCE_TYPE,
                        metricsReportingConsumer);

        // policies always refer to themselves in the cache.
        final Cache<EntityId, Entry<EntityId>> policyIdCache = new IdentityCache();

        final EnforcerCacheLoader enforcerCacheLoader =
                new EnforcerCacheLoader(askTimeout, thingsShardRegionProxy, policiesShardRegionProxy);
        final Cache<EntityId, Entry<Enforcer>> enforcerCache =
                CacheFactory.createCache(enforcerCacheLoader, configReader.caches().enforcer(),
                        ENFORCER_CACHE_METRIC_NAME,
                        metricsReportingConsumer);

        final Set<EnforcementProvider<?>> enforcementProviders = new HashSet<>();
        enforcementProviders.add(new ThingCommandEnforcement.Provider(thingsShardRegionProxy,
                policiesShardRegionProxy, thingIdCache, policyIdCache, enforcerCache));
        enforcementProviders.add(new PolicyCommandEnforcement.Provider(policiesShardRegionProxy,
                policyIdCache, enforcerCache));
        enforcementProviders.add(new MessageCommandEnforcement.Provider(thingIdCache, enforcerCache));
        enforcementProviders.add(new LiveSignalEnforcement.Provider(thingIdCache, enforcerCache));

        final Props enforcerProps = EnforcerActor.props(pubSubMediator, enforcementProviders);

        // start cache updaters
        final int instanceIndex = configReader.instanceIndex();
        final Props thingCacheUpdateActorProps =
                ThingCacheUpdateActor.props(enforcerCache, thingIdCache, pubSubMediator, instanceIndex);
        context.actorOf(thingCacheUpdateActorProps, ThingCacheUpdateActor.ACTOR_NAME);
        final Props policyCacheUpdateActorProps =
                PolicyCacheUpdateActor.props(enforcerCache, pubSubMediator, instanceIndex);
        context.actorOf(policyCacheUpdateActorProps, PolicyCacheUpdateActor.ACTOR_NAME);

        return enforcerProps;
    }
}
