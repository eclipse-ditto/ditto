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
package org.eclipse.ditto.policies.enforcement.pre_enforcement;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CacheFactory;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.cacheloaders.PreEnforcementPolicyIdCacheLoader;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionProxyActorFactory;
import org.eclipse.ditto.internal.utils.cluster.config.DefaultClusterConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.policies.api.PoliciesMessagingConstants;
import org.eclipse.ditto.policies.enforcement.config.DefaultEnforcementConfig;
import org.eclipse.ditto.policies.enforcement.config.EnforcementConfig;
import org.eclipse.ditto.policies.model.PolicyConstants;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.MessageDispatcher;

/**
 * Checks the existence of the entity from a Policy command.
 * @since 3.0.0
 */
public final class PolicyExistenceChecker implements ExistenceChecker {

    private static final String ID_CACHE_METRIC_NAME_PREFIX = "ditto_pre_enforcement_id_cache_";

    private final Map<EntityType, Cache<EnforcementCacheKey, ? extends Entry>> resourceToCacheMap;

    public PolicyExistenceChecker(final ActorSystem actorSystem) {
        final var enforcementConfig = DefaultEnforcementConfig.of(
                DefaultScopedConfig.dittoScoped(actorSystem.settings().config()));
        resourceToCacheMap = buildResourceToCacheMap(getPolicyIdCache(actorSystem, enforcementConfig));
    }

    private Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> getPolicyIdCache(final ActorSystem actorSystem,
            final EnforcementConfig enforcementConfig) {

        final var clusterConfig = DefaultClusterConfig.of(actorSystem.settings().config().getConfig("ditto.cluster"));
        final ShardRegionProxyActorFactory shardRegionProxyActorFactory =
                ShardRegionProxyActorFactory.newInstance(actorSystem, clusterConfig);

        final ActorRef policiesShardRegion = shardRegionProxyActorFactory.getShardRegionProxyActor(
                PoliciesMessagingConstants.CLUSTER_ROLE, PoliciesMessagingConstants.SHARD_REGION);

        final AsyncCacheLoader<EnforcementCacheKey, Entry<EnforcementCacheKey>> policyIdCacheLoader =
                new PreEnforcementPolicyIdCacheLoader(enforcementConfig.getAskWithRetryConfig(),
                        actorSystem.getScheduler(),
                        policiesShardRegion);
        final MessageDispatcher enforcementCacheDispatcher =
                actorSystem.dispatchers().lookup("enforcement-cache-dispatcher");

        return CacheFactory.createCache(policyIdCacheLoader, enforcementConfig.getIdCacheConfig(),
                ID_CACHE_METRIC_NAME_PREFIX + PolicyCommand.RESOURCE_TYPE, enforcementCacheDispatcher);
    }

    private static Map<EntityType, Cache<EnforcementCacheKey, ? extends Entry>> buildResourceToCacheMap(
            final Cache<EnforcementCacheKey, ? extends Entry> policyEnforcerCache) {

        final Map<EntityType, Cache<EnforcementCacheKey, ? extends Entry>> map = new HashMap<>();
        map.put(PolicyConstants.ENTITY_TYPE, policyEnforcerCache);
        return map;
    }

    @Override
    public CompletionStage<Boolean> checkExistence(final Signal<?> signal) {
        final Optional<EntityId> entityIdOptional = WithEntityId.getEntityIdOfType(EntityId.class, signal);
        final Optional<Cache<EnforcementCacheKey, ? extends Entry>> cacheOptional = entityIdOptional
                .map(EntityId::getEntityType)
                .map(resourceToCacheMap::get);

        if (cacheOptional.isEmpty() || entityIdOptional.isEmpty()) {
            final String message =
                    String.format("ExistenceChecker: unknown entity type or empty ID <%s:%s> for signal <%s>",
                            entityIdOptional.map(EntityId::getEntityType).map(Objects::toString).orElse(""),
                            entityIdOptional.map(Objects::toString).orElse(""), signal.toString());
            throw new IllegalArgumentException(message);
        } else {
            return cacheOptional.get().get(EnforcementCacheKey.of(entityIdOptional.get()))
                    .thenApply(entryOptional -> entryOptional.map(Entry::exists).orElse(false));
        }
    }

}
