/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.starter.proxy;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.base.config.ServiceConfigReader;
import org.eclipse.ditto.services.concierge.cache.AclEnforcerCacheLoader;
import org.eclipse.ditto.services.concierge.cache.CacheFactory;
import org.eclipse.ditto.services.concierge.cache.PolicyEnforcerCacheLoader;
import org.eclipse.ditto.services.concierge.cache.ThingEnforcementIdCacheLoader;
import org.eclipse.ditto.services.concierge.cache.update.PolicyCacheUpdateActor;
import org.eclipse.ditto.services.concierge.enforcement.EnforcementProvider;
import org.eclipse.ditto.services.concierge.enforcement.EnforcerActorCreator;
import org.eclipse.ditto.services.concierge.enforcement.LiveSignalEnforcement;
import org.eclipse.ditto.services.concierge.enforcement.PolicyCommandEnforcement;
import org.eclipse.ditto.services.concierge.enforcement.ThingCommandEnforcement;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.PlaceholderSubstitution;
import org.eclipse.ditto.services.concierge.starter.actors.DispatcherActorCreator;
import org.eclipse.ditto.services.concierge.util.config.ConciergeConfigReader;
import org.eclipse.ditto.services.models.concierge.ConciergeMessagingConstants;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.concierge.actors.ConciergeForwarderActor;
import org.eclipse.ditto.services.models.concierge.cache.Entry;
import org.eclipse.ditto.services.models.policies.PoliciesMessagingConstants;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.sharding.ClusterSharding;

/**
 * Ditto default implementation of {@link AbstractEnforcerActorFactory}.
 */
public final class DefaultEnforcerActorFactory extends AbstractEnforcerActorFactory<ConciergeConfigReader> {

    private static final String ENFORCER_CACHE_METRIC_NAME_PREFIX = "ditto_authorization_enforcer_cache_";
    private static final String ID_CACHE_METRIC_NAME_PREFIX = "ditto_authorization_id_cache_";

    @Override
    public ActorRef startEnforcerActor(final ActorContext context, final ConciergeConfigReader configReader,
            final ActorRef pubSubMediator) {
        final Duration askTimeout = configReader.caches().askTimeout();


        final ActorRef policiesShardRegionProxy = startProxy(context.system(), configReader.cluster().numberOfShards(),
                PoliciesMessagingConstants.SHARD_REGION, PoliciesMessagingConstants.CLUSTER_ROLE);

        final ActorRef thingsShardRegionProxy = startProxy(context.system(), configReader.cluster().numberOfShards(),
                ThingsMessagingConstants.SHARD_REGION, ThingsMessagingConstants.CLUSTER_ROLE);

        final ThingEnforcementIdCacheLoader thingEnforcerIdCacheLoader =
                new ThingEnforcementIdCacheLoader(askTimeout, thingsShardRegionProxy);
        final Cache<EntityId, Entry<EntityId>> thingIdCache =
                CacheFactory.createCache(thingEnforcerIdCacheLoader, configReader.caches().id(),
                        ID_CACHE_METRIC_NAME_PREFIX + ThingCommand.RESOURCE_TYPE);

        final PolicyEnforcerCacheLoader policyEnforcerCacheLoader =
                new PolicyEnforcerCacheLoader(askTimeout, policiesShardRegionProxy);
        final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache =
                CacheFactory.createCache(policyEnforcerCacheLoader, configReader.caches().enforcer(),
                        ENFORCER_CACHE_METRIC_NAME_PREFIX + "policy");

        final AclEnforcerCacheLoader aclEnforcerCacheLoader =
                new AclEnforcerCacheLoader(askTimeout, thingsShardRegionProxy);
        final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache =
                CacheFactory.createCache(aclEnforcerCacheLoader, configReader.caches().enforcer(),
                        ENFORCER_CACHE_METRIC_NAME_PREFIX + "acl");

        final Set<EnforcementProvider<?>> enforcementProviders = new HashSet<>();
        enforcementProviders.add(new ThingCommandEnforcement.Provider(thingsShardRegionProxy,
                policiesShardRegionProxy, thingIdCache, policyEnforcerCache, aclEnforcerCache));
        enforcementProviders.add(new PolicyCommandEnforcement.Provider(policiesShardRegionProxy, policyEnforcerCache));
        enforcementProviders.add(new LiveSignalEnforcement.Provider(thingIdCache, policyEnforcerCache,
                aclEnforcerCache));

        final Duration enforcementAskTimeout = configReader.enforcement().askTimeout();
        // set activity check interval identical to cache retention
        final Duration activityCheckInterval = configReader.caches().id().expireAfterWrite();
        final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer =
                PlaceholderSubstitution.newInstance();
        final ActorRef conciergeForwarder = getInternalConciergeForwarder(context, configReader, pubSubMediator);
        final Props enforcerProps =
                EnforcerActorCreator.props(pubSubMediator, enforcementProviders, enforcementAskTimeout,
                        conciergeForwarder, preEnforcer, activityCheckInterval);
        final ActorRef enforcerShardRegion = startShardRegion(context.system(), configReader.cluster(), enforcerProps);

        // start cache updaters
        final String instanceIndex = configReader.instanceIndex();
        final Props policyCacheUpdateActorProps =
                PolicyCacheUpdateActor.props(policyEnforcerCache, pubSubMediator, instanceIndex);
        context.actorOf(policyCacheUpdateActorProps, PolicyCacheUpdateActor.ACTOR_NAME);

        context.actorOf(DispatcherActorCreator.props(configReader, pubSubMediator, enforcerShardRegion),
                DispatcherActorCreator.ACTOR_NAME);

        return enforcerShardRegion;
    }


    private ActorRef getInternalConciergeForwarder(final ActorContext actorContext,
            final ServiceConfigReader configReader, final ActorRef pubSubMediator) {
        final ActorRef conciergeShardRegionProxy = ClusterSharding.get(actorContext.system())
                .startProxy(ConciergeMessagingConstants.SHARD_REGION,
                        Optional.of(ConciergeMessagingConstants.CLUSTER_ROLE),

                        ShardRegionExtractor.of(configReader.cluster().numberOfShards(),
                                actorContext.system()));

        return actorContext.actorOf(
                ConciergeForwarderActor.props(pubSubMediator, conciergeShardRegionProxy),
                "internal" + ConciergeForwarderActor.ACTOR_NAME);
    }

}
