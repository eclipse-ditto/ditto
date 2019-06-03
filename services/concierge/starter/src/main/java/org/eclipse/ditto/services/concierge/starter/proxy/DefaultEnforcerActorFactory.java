/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.starter.proxy;

import static org.eclipse.ditto.services.models.concierge.ConciergeMessagingConstants.CLUSTER_ROLE;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.concierge.cache.config.CachesConfig;
import org.eclipse.ditto.services.concierge.cache.update.PolicyCacheUpdateActor;
import org.eclipse.ditto.services.concierge.enforcement.EnforcementProvider;
import org.eclipse.ditto.services.concierge.enforcement.EnforcerActor;
import org.eclipse.ditto.services.concierge.enforcement.LiveSignalEnforcement;
import org.eclipse.ditto.services.concierge.enforcement.PolicyCommandEnforcement;
import org.eclipse.ditto.services.concierge.enforcement.ThingCommandEnforcement;
import org.eclipse.ditto.services.concierge.enforcement.config.EnforcementConfig;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.PlaceholderSubstitution;
import org.eclipse.ditto.services.concierge.enforcement.validators.CommandWithOptionalEntityValidator;
import org.eclipse.ditto.services.concierge.starter.actors.CachedNamespaceInvalidator;
import org.eclipse.ditto.services.concierge.starter.actors.DispatcherActor;
import org.eclipse.ditto.services.concierge.starter.config.ConciergeConfig;
import org.eclipse.ditto.services.models.concierge.ConciergeMessagingConstants;
import org.eclipse.ditto.services.models.concierge.actors.ConciergeEnforcerClusterRouterFactory;
import org.eclipse.ditto.services.models.concierge.actors.ConciergeForwarderActor;
import org.eclipse.ditto.services.models.policies.PoliciesMessagingConstants;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CacheFactory;
import org.eclipse.ditto.services.utils.cache.EntityId;
import org.eclipse.ditto.services.utils.cache.entry.Entry;
import org.eclipse.ditto.services.utils.cacheloaders.AclEnforcerCacheLoader;
import org.eclipse.ditto.services.utils.cacheloaders.PolicyEnforcerCacheLoader;
import org.eclipse.ditto.services.utils.cacheloaders.ThingEnforcementIdCacheLoader;
import org.eclipse.ditto.services.utils.cluster.ClusterUtil;
import org.eclipse.ditto.services.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.services.utils.namespaces.BlockNamespaceBehavior;
import org.eclipse.ditto.services.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.services.utils.namespaces.BlockedNamespacesUpdater;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;

/**
 * Ditto default implementation of{@link AbstractEnforcerActorFactory}.
 */
public final class DefaultEnforcerActorFactory extends AbstractEnforcerActorFactory<ConciergeConfig> {

    /**
     * Default namespace for {@code CreateThing} commands without any namespace.
     */
    private static final String DEFAULT_NAMESPACE = "org.eclipse.ditto";

    private static final String ENFORCER_CACHE_METRIC_NAME_PREFIX = "ditto_authorization_enforcer_cache_";
    private static final String ID_CACHE_METRIC_NAME_PREFIX = "ditto_authorization_id_cache_";

    @Override
    public ActorRef startEnforcerActor(final ActorContext context, final ConciergeConfig conciergeConfig,
            final ActorRef pubSubMediator) {

        final CachesConfig cachesConfig = conciergeConfig.getCachesConfig();
        final Duration askTimeout = cachesConfig.getAskTimeout();
        final ActorSystem actorSystem = context.system();

        final ClusterConfig clusterConfig = conciergeConfig.getClusterConfig();
        final int numberOfShards = clusterConfig.getNumberOfShards();

        final ActorRef policiesShardRegionProxy = startProxy(actorSystem, numberOfShards,
                PoliciesMessagingConstants.SHARD_REGION, PoliciesMessagingConstants.CLUSTER_ROLE);

        final ActorRef thingsShardRegionProxy = startProxy(actorSystem, numberOfShards,
                ThingsMessagingConstants.SHARD_REGION, ThingsMessagingConstants.CLUSTER_ROLE);

        final AsyncCacheLoader<EntityId, Entry<EntityId>> thingEnforcerIdCacheLoader =
                new ThingEnforcementIdCacheLoader(askTimeout, thingsShardRegionProxy);
        final Cache<EntityId, Entry<EntityId>> thingIdCache =
                CacheFactory.createCache(thingEnforcerIdCacheLoader, cachesConfig.getIdCacheConfig(),
                        ID_CACHE_METRIC_NAME_PREFIX + ThingCommand.RESOURCE_TYPE,
                        actorSystem.dispatchers().lookup("thing-id-cache-dispatcher"));

        final AsyncCacheLoader<EntityId, Entry<Enforcer>> policyEnforcerCacheLoader =
                new PolicyEnforcerCacheLoader(askTimeout, policiesShardRegionProxy);
        final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache =
                CacheFactory.createCache(policyEnforcerCacheLoader, cachesConfig.getEnforcerCacheConfig(),
                        ENFORCER_CACHE_METRIC_NAME_PREFIX + "policy",
                        actorSystem.dispatchers().lookup("policy-enforcer-cache-dispatcher"));

        final AsyncCacheLoader<EntityId, Entry<Enforcer>> aclEnforcerCacheLoader =
                new AclEnforcerCacheLoader(askTimeout, thingsShardRegionProxy);
        final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache =
                CacheFactory.createCache(aclEnforcerCacheLoader, cachesConfig.getEnforcerCacheConfig(),
                        ENFORCER_CACHE_METRIC_NAME_PREFIX + "acl",
                        actorSystem.dispatchers().lookup("acl-enforcer-cache-dispatcher"));

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

        final ActorRef conciergeEnforcerRouter =
                ConciergeEnforcerClusterRouterFactory.createConciergeEnforcerClusterRouter(context, numberOfShards);

        final EnforcementConfig enforcementConfig = conciergeConfig.getEnforcementConfig();

        context.actorOf(DispatcherActor.props(pubSubMediator, conciergeEnforcerRouter,
                enforcementConfig.getBufferSize(), enforcementConfig.getParallelism()), DispatcherActor.ACTOR_NAME);

        final ActorRef conciergeForwarder =
                context.actorOf(ConciergeForwarderActor.props(pubSubMediator, conciergeEnforcerRouter),
                        ConciergeForwarderActor.ACTOR_NAME);
        pubSubMediator.tell(new DistributedPubSubMediator.Put(conciergeForwarder), ActorRef.noSender());

        // start cache updaters
        final String instanceIndex = InstanceIdentifierSupplier.getInstance().get();
        final Props policyCacheUpdateActorProps =
                PolicyCacheUpdateActor.props(policyEnforcerCache, pubSubMediator, instanceIndex);
        context.actorOf(policyCacheUpdateActorProps, PolicyCacheUpdateActor.ACTOR_NAME);

        final Props cachedNamespaceInvalidatorProps =
                CachedNamespaceInvalidator.props(blockedNamespaces,
                        Arrays.asList(thingIdCache, policyEnforcerCache, aclEnforcerCache));
        context.actorOf(cachedNamespaceInvalidatorProps, CachedNamespaceInvalidator.ACTOR_NAME);

        // start cluster singleton that writes to the distributed cache of blocked namespaces
        final Props blockedNamespacesUpdaterProps = BlockedNamespacesUpdater.props(blockedNamespaces, pubSubMediator);
        ClusterUtil.startSingleton(actorSystem, actorSystem, CLUSTER_ROLE,
                ConciergeMessagingConstants.BLOCKED_NAMESPACES_UPDATER_NAME,
                blockedNamespacesUpdaterProps);

        final Duration enforcementAskTimeout = enforcementConfig.getAskTimeout();
        final Executor enforcerExecutor = actorSystem.dispatchers().lookup(ENFORCER_DISPATCHER);
        final Props enforcerProps =
                EnforcerActor.props(pubSubMediator, enforcementProviders, enforcementAskTimeout, conciergeForwarder,
                        enforcerExecutor, enforcementConfig.getBufferSize(), enforcementConfig.getParallelism(),
                        preEnforcer, thingIdCache, aclEnforcerCache,
                        policyEnforcerCache) // passes in the caches to be able to invalidate cache entries
                        .withDispatcher(ENFORCER_DISPATCHER);

        return context.actorOf(enforcerProps, EnforcerActor.ACTOR_NAME);
    }

    private static Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> newPreEnforcer(
            final BlockedNamespaces blockedNamespaces, final PlaceholderSubstitution placeholderSubstitution) {

        return withDittoHeaders ->
                BlockNamespaceBehavior.of(blockedNamespaces)
                        .block(withDittoHeaders)
                        .thenApply(CommandWithOptionalEntityValidator.getInstance())
                        .thenApply(DefaultEnforcerActorFactory::prependDefaultNamespaceToCreateThing)
                        .thenCompose(placeholderSubstitution);
    }

    private static WithDittoHeaders prependDefaultNamespaceToCreateThing(final WithDittoHeaders signal) {
        if (signal instanceof CreateThing) {
            final CreateThing createThing = (CreateThing) signal;
            if (!createThing.getThing().getNamespace().isPresent()) {
                final Thing thingInDefaultNamespace = createThing.getThing()
                        .toBuilder()
                        .setId(DEFAULT_NAMESPACE + createThing.getThingId())
                        .build();
                final JsonObject initialPolicy = createThing.getInitialPolicy().orElse(null);
                return CreateThing.of(thingInDefaultNamespace, initialPolicy, createThing.getDittoHeaders());
            }
        }
        return signal;
    }

}
