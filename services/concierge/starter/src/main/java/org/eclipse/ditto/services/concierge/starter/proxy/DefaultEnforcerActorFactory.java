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
import java.util.Optional;
import java.util.Set;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersSettable;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.services.concierge.actors.ShardRegions;
import org.eclipse.ditto.services.concierge.common.CachesConfig;
import org.eclipse.ditto.services.concierge.common.ConciergeConfig;
import org.eclipse.ditto.services.concierge.enforcement.EnforcementProvider;
import org.eclipse.ditto.services.concierge.enforcement.EnforcerActor;
import org.eclipse.ditto.services.concierge.enforcement.LiveSignalEnforcement;
import org.eclipse.ditto.services.concierge.enforcement.PolicyCommandEnforcement;
import org.eclipse.ditto.services.concierge.enforcement.PreEnforcer;
import org.eclipse.ditto.services.concierge.enforcement.ThingCommandEnforcement;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.PlaceholderSubstitution;
import org.eclipse.ditto.services.concierge.enforcement.validators.CommandWithOptionalEntityValidator;
import org.eclipse.ditto.services.concierge.starter.actors.CachedNamespaceInvalidator;
import org.eclipse.ditto.services.concierge.starter.actors.DispatcherActor;
import org.eclipse.ditto.services.models.concierge.ConciergeMessagingConstants;
import org.eclipse.ditto.services.models.concierge.actors.ConciergeEnforcerClusterRouterFactory;
import org.eclipse.ditto.services.models.concierge.actors.ConciergeForwarderActor;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CacheFactory;
import org.eclipse.ditto.services.utils.cache.CacheKey;
import org.eclipse.ditto.services.utils.cache.entry.Entry;
import org.eclipse.ditto.services.utils.cacheloaders.PolicyEnforcer;
import org.eclipse.ditto.services.utils.cacheloaders.PolicyEnforcerCacheLoader;
import org.eclipse.ditto.services.utils.cacheloaders.ThingEnforcementIdCacheLoader;
import org.eclipse.ditto.services.utils.cluster.ClusterUtil;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.services.utils.namespaces.BlockNamespaceBehavior;
import org.eclipse.ditto.services.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.services.utils.namespaces.BlockedNamespacesUpdater;
import org.eclipse.ditto.services.utils.pubsub.DistributedAcks;
import org.eclipse.ditto.services.utils.pubsub.LiveSignalPub;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Ditto default implementation of{@link EnforcerActorFactory}.
 */
public final class DefaultEnforcerActorFactory implements EnforcerActorFactory<ConciergeConfig> {

    /**
     * Default namespace for {@code CreateThing} commands without any namespace.
     */
    private static final String DEFAULT_NAMESPACE = "org.eclipse.ditto";

    private static final String ENFORCER_CACHE_METRIC_NAME_PREFIX = "ditto_authorization_enforcer_cache_";
    private static final String ID_CACHE_METRIC_NAME_PREFIX = "ditto_authorization_id_cache_";

    @Override
    public ActorRef startEnforcerActor(final ActorContext context, final ConciergeConfig conciergeConfig,
            final ActorRef pubSubMediator, final ShardRegions shardRegions) {

        final CachesConfig cachesConfig = conciergeConfig.getCachesConfig();
        final Duration askTimeout = cachesConfig.getAskTimeout();
        final ActorSystem actorSystem = context.system();

        final ActorRef policiesShardRegionProxy = shardRegions.policies();

        final ActorRef thingsShardRegionProxy = shardRegions.things();

        final AsyncCacheLoader<CacheKey, Entry<CacheKey>> thingEnforcerIdCacheLoader =
                new ThingEnforcementIdCacheLoader(askTimeout, thingsShardRegionProxy);
        final Cache<CacheKey, Entry<CacheKey>> thingIdCache =
                CacheFactory.createCache(thingEnforcerIdCacheLoader, cachesConfig.getIdCacheConfig(),
                        ID_CACHE_METRIC_NAME_PREFIX + ThingCommand.RESOURCE_TYPE,
                        actorSystem.dispatchers().lookup("thing-id-cache-dispatcher"));

        final AsyncCacheLoader<CacheKey, Entry<PolicyEnforcer>> policyEnforcerCacheLoader =
                new PolicyEnforcerCacheLoader(askTimeout, policiesShardRegionProxy);
        final Cache<CacheKey, Entry<PolicyEnforcer>> policyEnforcerCache =
                CacheFactory.createCache(policyEnforcerCacheLoader, cachesConfig.getEnforcerCacheConfig(),
                        ENFORCER_CACHE_METRIC_NAME_PREFIX + "policy",
                        actorSystem.dispatchers().lookup("policy-enforcer-cache-dispatcher"));
        final Cache<CacheKey, Entry<Enforcer>> projectedEnforcerCache =
                policyEnforcerCache.projectValues(PolicyEnforcer::project, PolicyEnforcer::embed);

        // pre-enforcer
        final BlockedNamespaces blockedNamespaces = BlockedNamespaces.of(actorSystem);
        final PreEnforcer preEnforcer = newPreEnforcer(blockedNamespaces, PlaceholderSubstitution.newInstance());

        final DistributedAcks distributedAcks = DistributedAcks.lookup(actorSystem);
        final LiveSignalPub liveSignalPub = LiveSignalPub.of(context, distributedAcks);

        final Set<EnforcementProvider<?>> enforcementProviders = new HashSet<>();
        enforcementProviders.add(new ThingCommandEnforcement.Provider(thingsShardRegionProxy,
                policiesShardRegionProxy, thingIdCache, projectedEnforcerCache, preEnforcer));
        enforcementProviders.add(new PolicyCommandEnforcement.Provider(policiesShardRegionProxy, policyEnforcerCache));
        enforcementProviders.add(
                new LiveSignalEnforcement.Provider(thingIdCache, projectedEnforcerCache, liveSignalPub));

        final ActorRef conciergeEnforcerRouter =
                ConciergeEnforcerClusterRouterFactory.createConciergeEnforcerClusterRouter(context,
                        conciergeConfig.getClusterConfig().getNumberOfShards());

        context.actorOf(DispatcherActor.props(pubSubMediator, conciergeEnforcerRouter),
                DispatcherActor.ACTOR_NAME);

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
                        policyEnforcerCache);

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
