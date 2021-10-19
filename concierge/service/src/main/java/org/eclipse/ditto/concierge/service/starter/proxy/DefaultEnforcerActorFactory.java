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
package org.eclipse.ditto.concierge.service.starter.proxy;

import static org.eclipse.ditto.concierge.api.ConciergeMessagingConstants.CLUSTER_ROLE;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.concierge.api.ConciergeMessagingConstants;
import org.eclipse.ditto.concierge.api.actors.ConciergeEnforcerClusterRouterFactory;
import org.eclipse.ditto.concierge.api.actors.ConciergeForwarderActor;
import org.eclipse.ditto.concierge.service.actors.ShardRegions;
import org.eclipse.ditto.concierge.service.common.ConciergeConfig;
import org.eclipse.ditto.concierge.service.enforcement.EnforcementProvider;
import org.eclipse.ditto.concierge.service.enforcement.EnforcerActor;
import org.eclipse.ditto.concierge.service.enforcement.InvalidateCacheEntry;
import org.eclipse.ditto.concierge.service.enforcement.LiveSignalEnforcement;
import org.eclipse.ditto.concierge.service.enforcement.PolicyCommandEnforcement;
import org.eclipse.ditto.concierge.service.enforcement.PreEnforcer;
import org.eclipse.ditto.concierge.service.enforcement.ThingCommandEnforcement;
import org.eclipse.ditto.concierge.service.enforcement.placeholders.PlaceholderSubstitution;
import org.eclipse.ditto.concierge.service.enforcement.validators.CommandWithOptionalEntityValidator;
import org.eclipse.ditto.concierge.service.starter.actors.CachedNamespaceInvalidator;
import org.eclipse.ditto.concierge.service.starter.actors.DispatcherActor;
import org.eclipse.ditto.concierge.service.starter.actors.PolicyCacheUpdateActor;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CacheFactory;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.cacheloaders.PolicyCacheLoader;
import org.eclipse.ditto.internal.utils.cacheloaders.PolicyEnforcer;
import org.eclipse.ditto.internal.utils.cacheloaders.PolicyEnforcerCacheLoader;
import org.eclipse.ditto.internal.utils.cacheloaders.ThingEnforcementIdCacheLoader;
import org.eclipse.ditto.internal.utils.cluster.ClusterUtil;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.namespaces.BlockNamespaceBehavior;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespacesUpdater;
import org.eclipse.ditto.internal.utils.pubsub.DistributedAcks;
import org.eclipse.ditto.internal.utils.pubsub.LiveSignalPub;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Ditto default implementation of{@link EnforcerActorFactory}.
 */
public final class DefaultEnforcerActorFactory implements EnforcerActorFactory<ConciergeConfig> {

    /**
     * Default namespace for {@code CreateThing} commands without any namespace.
     */
    private static final String DEFAULT_NAMESPACE = "org.eclipse.ditto";

    private static final String POLICY_CACHE_METRIC_NAME_PREFIX = "ditto_authorization_policy_cache_";
    private static final String ENFORCER_CACHE_METRIC_NAME_PREFIX = "ditto_authorization_enforcer_cache_";
    private static final String ID_CACHE_METRIC_NAME_PREFIX = "ditto_authorization_id_cache_";

    @Override
    public ActorRef startEnforcerActor(final ActorContext context, final ConciergeConfig conciergeConfig,
            final ActorRef pubSubMediator, final ShardRegions shardRegions) {

        final var cachesConfig = conciergeConfig.getCachesConfig();
        final var askWithRetryConfig = cachesConfig.getAskWithRetryConfig();
        final var actorSystem = context.system();

        final ActorRef policiesShardRegionProxy = shardRegions.policies();
        final ActorRef thingsShardRegionProxy = shardRegions.things();

        final AsyncCacheLoader<EnforcementCacheKey, Entry<EnforcementCacheKey>> thingEnforcerIdCacheLoader =
                new ThingEnforcementIdCacheLoader(askWithRetryConfig, actorSystem.getScheduler(),
                        thingsShardRegionProxy);
        final Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> thingIdCache =
                CacheFactory.createCache(thingEnforcerIdCacheLoader, cachesConfig.getIdCacheConfig(),
                        ID_CACHE_METRIC_NAME_PREFIX + ThingCommand.RESOURCE_TYPE,
                        actorSystem.dispatchers().lookup("thing-id-cache-dispatcher"));

        final PolicyCacheLoader policyCacheLoader =
                new PolicyCacheLoader(askWithRetryConfig, actorSystem.getScheduler(), policiesShardRegionProxy);
        final Cache<EnforcementCacheKey, Entry<Policy>> policyCache =
                CacheFactory.createCache(policyCacheLoader, cachesConfig.getPolicyCacheConfig(),
                        POLICY_CACHE_METRIC_NAME_PREFIX + PolicyCommand.RESOURCE_TYPE,
                        actorSystem.dispatchers().lookup("policy-cache-dispatcher"));

        final PolicyEnforcerCacheLoader policyEnforcerCacheLoader = new PolicyEnforcerCacheLoader(policyCache);
        final Cache<EnforcementCacheKey, Entry<PolicyEnforcer>> policyEnforcerCache =
                CacheFactory.createCache(policyEnforcerCacheLoader, cachesConfig.getEnforcerCacheConfig(),
                        ENFORCER_CACHE_METRIC_NAME_PREFIX + PolicyCommand.RESOURCE_TYPE,
                        actorSystem.dispatchers().lookup("policy-enforcer-cache-dispatcher"));
        policyEnforcerCache.subscribeForInvalidation((k,v,r) ->
                policyEnforcerCacheLoader.onCacheEntryInvalidated(k, null, r));

        final Cache<EnforcementCacheKey, Entry<Enforcer>> projectedEnforcerCache =
                policyEnforcerCache.projectValues(PolicyEnforcer::project, PolicyEnforcer::embed);

        policyEnforcerCacheLoader.registerCacheInvalidator(cacheKey -> {
            policyCache.invalidate(cacheKey);
            projectedEnforcerCache.invalidate(cacheKey);
            // cache invalidation must be done cluster wide also on all concierge instances:
            pubSubMediator.tell(DistPubSubAccess.sendToAll(
                            ConciergeMessagingConstants.ENFORCER_ACTOR_PATH,
                            InvalidateCacheEntry.of(cacheKey),
                            true),
                    ActorRef.noSender());
        });

        // pre-enforcer
        final BlockedNamespaces blockedNamespaces = BlockedNamespaces.of(actorSystem);
        final PreEnforcer preEnforcer = newPreEnforcer(blockedNamespaces, PlaceholderSubstitution.newInstance());

        final DistributedAcks distributedAcks = DistributedAcks.lookup(actorSystem);
        final LiveSignalPub liveSignalPub = LiveSignalPub.of(context, distributedAcks);

        final Set<EnforcementProvider<?>> enforcementProviders = new HashSet<>();
        enforcementProviders.add(new ThingCommandEnforcement.Provider(thingsShardRegionProxy,
                policiesShardRegionProxy, thingIdCache, policyCache, projectedEnforcerCache, preEnforcer));
        enforcementProviders.add(new PolicyCommandEnforcement.Provider(policiesShardRegionProxy, policyCache,
                policyEnforcerCache));
        enforcementProviders.add(
                new LiveSignalEnforcement.Provider(thingIdCache, projectedEnforcerCache, liveSignalPub));

        final ActorRef conciergeEnforcerRouter =
                ConciergeEnforcerClusterRouterFactory.createConciergeEnforcerClusterRouter(context,
                        conciergeConfig.getClusterConfig().getNumberOfShards());

        context.actorOf(DispatcherActor.props(pubSubMediator, conciergeEnforcerRouter),
                DispatcherActor.ACTOR_NAME);
        final Props policyCacheUpdateActorProps =
                PolicyCacheUpdateActor.props(policyCache, policyEnforcerCache, pubSubMediator);
        context.actorOf(policyCacheUpdateActorProps, PolicyCacheUpdateActor.ACTOR_NAME);

        final ActorRef conciergeForwarder =
                context.actorOf(ConciergeForwarderActor.props(pubSubMediator, conciergeEnforcerRouter),
                        ConciergeForwarderActor.ACTOR_NAME);
        pubSubMediator.tell(DistPubSubAccess.put(conciergeForwarder), ActorRef.noSender());

        // start cache invalidator
        final Props cachedNamespaceInvalidatorProps =
                CachedNamespaceInvalidator.props(blockedNamespaces, Arrays.asList(thingIdCache, policyEnforcerCache));
        context.actorOf(cachedNamespaceInvalidatorProps, CachedNamespaceInvalidator.ACTOR_NAME);

        // start cluster singleton that writes to the distributed cache of blocked namespaces
        final Props blockedNamespacesUpdaterProps = BlockedNamespacesUpdater.props(blockedNamespaces, pubSubMediator);
        ClusterUtil.startSingleton(actorSystem, context, CLUSTER_ROLE,
                ConciergeMessagingConstants.BLOCKED_NAMESPACES_UPDATER_NAME,
                blockedNamespacesUpdaterProps);

        // passes in the caches to be able to invalidate cache entries
        final Props enforcerProps =
                EnforcerActor.props(pubSubMediator, enforcementProviders, conciergeForwarder, preEnforcer, thingIdCache,
                        policyCache, policyEnforcerCache);

        return context.actorOf(enforcerProps, EnforcerActor.ACTOR_NAME);
    }

    /**
     * Set the "ditto-originator" header to the primary authorization subject of a signal.
     *
     * @param originalSignal A signal with authorization context.
     * @return A copy of the signal with the header "ditto-originator" set.
     */
    public static DittoHeadersSettable<?> setOriginatorHeader(final DittoHeadersSettable<?> originalSignal) {
        final DittoHeaders dittoHeaders = originalSignal.getDittoHeaders();
        final AuthorizationContext authorizationContext = dittoHeaders.getAuthorizationContext();
        return authorizationContext.getFirstAuthorizationSubject()
                .map(AuthorizationSubject::getId)
                .map(originatorSubjectId -> DittoHeaders.newBuilder(dittoHeaders)
                        .putHeader(DittoHeaderDefinition.ORIGINATOR.getKey(), originatorSubjectId)
                        .build())
                .<DittoHeadersSettable<?>>map(originalSignal::setDittoHeaders)
                .orElse(originalSignal);
    }

    private static PreEnforcer newPreEnforcer(final BlockedNamespaces blockedNamespaces,
            final PlaceholderSubstitution placeholderSubstitution) {

        return dittoHeadersSettable ->
                BlockNamespaceBehavior.of(blockedNamespaces)
                        .block(dittoHeadersSettable)
                        .thenApply(CommandWithOptionalEntityValidator.getInstance())
                        .thenApply(DefaultEnforcerActorFactory::prependDefaultNamespaceToCreateThing)
                        .thenApply(DefaultEnforcerActorFactory::setOriginatorHeader)
                        .thenCompose(placeholderSubstitution);
    }

    private static DittoHeadersSettable<?> prependDefaultNamespaceToCreateThing(final DittoHeadersSettable<?> signal) {
        if (signal instanceof CreateThing) {
            final CreateThing createThing = (CreateThing) signal;
            final Thing thing = createThing.getThing();
            final Optional<String> namespace = thing.getNamespace();
            if (namespace.isEmpty()) {
                final Thing thingInDefaultNamespace = thing.toBuilder()
                        .setId(ThingId.of(DEFAULT_NAMESPACE, createThing.getEntityId().toString()))
                        .build();
                final JsonObject initialPolicy = createThing.getInitialPolicy().orElse(null);
                return CreateThing.of(thingInDefaultNamespace, initialPolicy, createThing.getDittoHeaders());
            }
        }
        return signal;
    }

}
