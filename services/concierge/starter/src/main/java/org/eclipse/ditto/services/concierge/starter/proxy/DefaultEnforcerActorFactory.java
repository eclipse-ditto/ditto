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

import static org.eclipse.ditto.services.models.concierge.ConciergeMessagingConstants.CLUSTER_ROLE;

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
import org.eclipse.ditto.services.concierge.enforcement.validators.CommandWithOptionalEntityValidator;
import org.eclipse.ditto.services.concierge.starter.actors.DispatcherActorCreator;
import org.eclipse.ditto.services.concierge.util.config.ConciergeConfigReader;
import org.eclipse.ditto.services.models.concierge.ConciergeMessagingConstants;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.concierge.actors.ConciergeForwarderActor;
import org.eclipse.ditto.services.models.concierge.cache.Entry;
import org.eclipse.ditto.services.models.policies.PoliciesMessagingConstants;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cluster.ClusterUtil;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.namespaces.BlockNamespaceBehavior;
import org.eclipse.ditto.services.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.services.utils.namespaces.BlockedNamespacesUpdater;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
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
        final ActorSystem actorSystem = context.system();


        final ActorRef policiesShardRegionProxy = startProxy(actorSystem, configReader.cluster().numberOfShards(),
                PoliciesMessagingConstants.SHARD_REGION, PoliciesMessagingConstants.CLUSTER_ROLE);

        final ActorRef thingsShardRegionProxy = startProxy(actorSystem, configReader.cluster().numberOfShards(),
                ThingsMessagingConstants.SHARD_REGION, ThingsMessagingConstants.CLUSTER_ROLE);

        final AsyncCacheLoader<EntityId, Entry<EntityId>> thingEnforcerIdCacheLoader =
                new ThingEnforcementIdCacheLoader(askTimeout, thingsShardRegionProxy);
        final Cache<EntityId, Entry<EntityId>> thingIdCache =
                CacheFactory.createCache(thingEnforcerIdCacheLoader, configReader.caches().id(),
                        ID_CACHE_METRIC_NAME_PREFIX + ThingCommand.RESOURCE_TYPE);

        final AsyncCacheLoader<EntityId, Entry<Enforcer>> policyEnforcerCacheLoader =
                new PolicyEnforcerCacheLoader(askTimeout, policiesShardRegionProxy);
        final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache =
                CacheFactory.createCache(policyEnforcerCacheLoader, configReader.caches().enforcer(),
                        ENFORCER_CACHE_METRIC_NAME_PREFIX + "policy");

        final AsyncCacheLoader<EntityId, Entry<Enforcer>> aclEnforcerCacheLoader =
                new AclEnforcerCacheLoader(askTimeout, thingsShardRegionProxy);
        final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache =
                CacheFactory.createCache(aclEnforcerCacheLoader, configReader.caches().enforcer(),
                        ENFORCER_CACHE_METRIC_NAME_PREFIX + "acl");

        // pre-enforcer
        final BlockedNamespaces blockedNamespaces = BlockedNamespaces.of(actorSystem);
        final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer =
                newPreEnforcer(blockedNamespaces, PlaceholderSubstitution.newInstance());

        final Set<EnforcementProvider<?>> enforcementProviders = new HashSet<>();
        enforcementProviders.add(new ThingCommandEnforcement.Provider(thingsShardRegionProxy,
                policiesShardRegionProxy, thingIdCache, policyEnforcerCache, aclEnforcerCache, preEnforcer));
        enforcementProviders.add(new PolicyCommandEnforcement.Provider(policiesShardRegionProxy, policyEnforcerCache));
        enforcementProviders.add(new LiveSignalEnforcement.Provider(thingIdCache, policyEnforcerCache,
                aclEnforcerCache));

        final Duration enforcementAskTimeout = configReader.enforcement().askTimeout();
        // set activity check interval identical to cache retention
        final Duration activityCheckInterval = configReader.caches().id().expireAfterWrite();
        final ActorRef conciergeForwarder = getInternalConciergeForwarder(context, configReader, pubSubMediator);
        final Props enforcerProps =
                EnforcerActorCreator.props(pubSubMediator, enforcementProviders, enforcementAskTimeout,
                        conciergeForwarder, preEnforcer, activityCheckInterval);
        final ActorRef enforcerShardRegion = startShardRegion(context.system(), configReader.cluster(), enforcerProps);

        // start cache updaters
        final String instanceIndex = ConfigUtil.instanceIdentifier();
        final Props policyCacheUpdateActorProps =
                PolicyCacheUpdateActor.props(policyEnforcerCache, pubSubMediator, instanceIndex);
        context.actorOf(policyCacheUpdateActorProps, PolicyCacheUpdateActor.ACTOR_NAME);

        // start cluster singleton that writes to the distributed cache of blocked namespaces
        final Props blockedNamespacesUpdaterProps = BlockedNamespacesUpdater.props(blockedNamespaces, pubSubMediator);
        ClusterUtil.startSingleton(actorSystem, actorSystem, CLUSTER_ROLE,
                ConciergeMessagingConstants.BLOCKED_NAMESPACES_UPDATER_NAME,
                blockedNamespacesUpdaterProps);

        context.actorOf(DispatcherActorCreator.props(configReader, pubSubMediator, enforcerShardRegion),
                DispatcherActorCreator.ACTOR_NAME);

        return enforcerShardRegion;
    }

    private static Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> newPreEnforcer(
            final BlockedNamespaces blockedNamespaces,
            final PlaceholderSubstitution placeholderSubstitution) {

        return withDittoHeaders ->
                BlockNamespaceBehavior.of(blockedNamespaces)
                        .block(withDittoHeaders)
                        .thenApply(CommandWithOptionalEntityValidator.getInstance())
                        .thenCompose(placeholderSubstitution);
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
