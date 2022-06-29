/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.cache.CacheFactory;
import org.eclipse.ditto.internal.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionProxyActorFactory;
import org.eclipse.ditto.internal.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.internal.utils.cluster.config.DefaultClusterConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.policies.api.PoliciesMessagingConstants;
import org.eclipse.ditto.policies.enforcement.config.DefaultEnforcementConfig;
import org.eclipse.ditto.policies.model.PolicyId;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.pubsub.DistributedPubSub;
import akka.dispatch.MessageDispatcher;

/**
 * Loads the {@link org.eclipse.ditto.policies.model.Policy} from the policies shard region and wraps it into a {@link PolicyEnforcer}.
 */
public final class DefaultPolicyEnforcerProvider implements PolicyEnforcerProvider {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(DefaultPolicyEnforcerProvider.class);

    private final AsyncCacheLoader<EnforcementCacheKey, Entry<PolicyEnforcer>> policyEnforcerCacheLoader;
    private final MessageDispatcher enforcementCacheDispatcher;

    DefaultPolicyEnforcerProvider(final ActorSystem actorSystem) {
        this(policyEnforcerCacheLoader(actorSystem), enforcementCacheDispatcher(actorSystem));
    }

    DefaultPolicyEnforcerProvider(
            final AsyncCacheLoader<EnforcementCacheKey, Entry<PolicyEnforcer>> policyEnforcerCacheLoader,
            final MessageDispatcher enforcementCacheDispatcher) {

        this.policyEnforcerCacheLoader = policyEnforcerCacheLoader;
        this.enforcementCacheDispatcher = enforcementCacheDispatcher;
    }

    /**
     * Creates a new instance of this policy enforcer provider based on the configuration in the actor system
     *
     * @param actorSystem used to initialize all dependencies of the policy enforcer provider
     * @return the new instance.
     */
    public static PolicyEnforcerProvider getInstance(final ActorSystem actorSystem) {
        final DefaultPolicyEnforcerProvider shardRegionPolicyEnforcerProvider =
                new DefaultPolicyEnforcerProvider(actorSystem);
        final boolean withCaching = actorSystem.settings().config().getBoolean("ditto.policies-enforcer-cache.enabled");
        if (withCaching) {
            return shardRegionPolicyEnforcerProvider.withCaching(actorSystem);
        } else {
            return shardRegionPolicyEnforcerProvider;
        }
    }

    private static AskWithRetryConfig askWithRetryConfig(final ActorSystem actorSystem) {
        final DefaultScopedConfig dittoScoped = DefaultScopedConfig.dittoScoped(actorSystem.settings().config());
        return DefaultEnforcementConfig.of(dittoScoped).getAskWithRetryConfig();
    }

    private static ActorRef policiesShardRegion(final ActorSystem actorSystem) {
        final var dittoScopedConfig = DefaultScopedConfig.dittoScoped(actorSystem.settings().config());
        final ClusterConfig clusterConfig = DefaultClusterConfig.of(dittoScopedConfig);
        final ShardRegionProxyActorFactory shardRegionProxyActorFactory =
                ShardRegionProxyActorFactory.newInstance(actorSystem, clusterConfig);

        return shardRegionProxyActorFactory.getShardRegionProxyActor(
                PoliciesMessagingConstants.CLUSTER_ROLE,
                PoliciesMessagingConstants.SHARD_REGION
        );
    }

    private static AsyncCacheLoader<EnforcementCacheKey, Entry<PolicyEnforcer>> policyEnforcerCacheLoader(
            final ActorSystem actorSystem) {
        final AskWithRetryConfig askWithRetryConfig = askWithRetryConfig(actorSystem);
        final ActorRef policiesShardRegion = policiesShardRegion(actorSystem);
        return new PolicyEnforcerCacheLoader(askWithRetryConfig, actorSystem.getScheduler(), policiesShardRegion);
    }

    private static MessageDispatcher enforcementCacheDispatcher(final ActorSystem actorSystem) {
        return actorSystem.dispatchers().lookup(PolicyEnforcerCacheLoader.ENFORCEMENT_CACHE_DISPATCHER);
    }

    /**
     * Loads the {@link org.eclipse.ditto.policies.model.Policy} from the policies shard region and wraps it into a {@link PolicyEnforcer}.
     *
     * @param policyId the ID of the policy that should be loaded.
     * @return A completion stage completing with an Optional holding the PolicyEnforcer in case it could be retrieved or an empty optional if not.
     */
    @Override
    public CompletionStage<Optional<PolicyEnforcer>> getPolicyEnforcer(@Nullable final PolicyId policyId) {
        if (null == policyId) {
            return CompletableFuture.completedStage(Optional.empty());
        } else {
            try {
                return policyEnforcerCacheLoader.asyncLoad(EnforcementCacheKey.of(policyId),
                                enforcementCacheDispatcher)
                        .thenApply(Entry::get)
                        .exceptionally(error -> Optional.empty());
            } catch (final Exception e) {
                LOGGER.warn(
                        "Got exception when trying to load the policy enforcer via cache loader. This is unexpected"
                );
                return CompletableFuture.completedStage(Optional.empty());
            }
        }
    }

    private PolicyEnforcerProvider withCaching(final ActorSystem actorSystem) {
        final var dispatchers = actorSystem.dispatchers();
        final var cacheConfig = DefaultCacheConfig.of(actorSystem.settings().config(), "ditto.policies-enforcer-cache");
        final var cacheDispatcher = dispatchers.lookup("enforcement-cache-dispatcher");
        final var policyEnforcerCache =
                CacheFactory.<PolicyId, Entry<PolicyEnforcer>>createCache(cacheConfig, "policy_enforcer_cache",
                        cacheDispatcher);
        final var pubSubMediator = DistributedPubSub.get(actorSystem).mediator();
        final var blockedNamespaces = BlockedNamespaces.of(actorSystem);
        return new CachingPolicyEnforcerProvider(actorSystem, policyEnforcerCache, this, blockedNamespaces,
                pubSubMediator);
    }

}
