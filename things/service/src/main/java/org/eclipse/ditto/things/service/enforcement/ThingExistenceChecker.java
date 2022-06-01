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
package org.eclipse.ditto.things.service.enforcement;

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
import org.eclipse.ditto.internal.utils.cacheloaders.PreEnforcementThingIdCacheLoader;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionProxyActorFactory;
import org.eclipse.ditto.internal.utils.cluster.config.DefaultClusterConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.policies.enforcement.config.DefaultEnforcementConfig;
import org.eclipse.ditto.policies.enforcement.config.EnforcementConfig;
import org.eclipse.ditto.policies.enforcement.pre_enforcement.ExistenceChecker;
import org.eclipse.ditto.things.api.ThingsMessagingConstants;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.MessageDispatcher;

public final class ThingExistenceChecker implements ExistenceChecker {

    private static final String ID_CACHE_METRIC_NAME_PREFIX = "ditto_pre_enforcement_id_cache_";

    private final Map<EntityType, Cache<EnforcementCacheKey, ? extends Entry>> resourceToCacheMap;

    public ThingExistenceChecker(final ActorSystem actorSystem) {
        final var enforcementConfig = DefaultEnforcementConfig.of(
                DefaultScopedConfig.dittoScoped(actorSystem.settings().config()));
        resourceToCacheMap = buildResourceToCacheMap(getThingIdCache(actorSystem, enforcementConfig));
    }

    private Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> getThingIdCache(final ActorSystem actorSystem,
            final EnforcementConfig enforcementConfig) {

        final var clusterConfig = DefaultClusterConfig.of(actorSystem.settings().config().getConfig("ditto.cluster"));
        final ShardRegionProxyActorFactory shardRegionProxyActorFactory =
                ShardRegionProxyActorFactory.newInstance(actorSystem, clusterConfig);

        final ActorRef thingsShardRegion = shardRegionProxyActorFactory.getShardRegionProxyActor(
                ThingsMessagingConstants.CLUSTER_ROLE,
                ThingsMessagingConstants.SHARD_REGION);
        final AsyncCacheLoader<EnforcementCacheKey, Entry<EnforcementCacheKey>> thingsIdCacheLoader =
                new PreEnforcementThingIdCacheLoader(enforcementConfig.getAskWithRetryConfig(),
                        actorSystem.getScheduler(),
                        thingsShardRegion);
        final MessageDispatcher enforcementCacheDispatcher =
                actorSystem.dispatchers().lookup("enforcement-cache-dispatcher");
        return CacheFactory.createCache(thingsIdCacheLoader, enforcementConfig.getIdCacheConfig(),
                ID_CACHE_METRIC_NAME_PREFIX + ThingCommand.RESOURCE_TYPE, enforcementCacheDispatcher);
    }

    private static Map<EntityType, Cache<EnforcementCacheKey, ? extends Entry>> buildResourceToCacheMap(
            final Cache<EnforcementCacheKey, ? extends Entry> thingIdCache) {

        final Map<EntityType, Cache<EnforcementCacheKey, ? extends Entry>> map = new HashMap<>();
        map.put(ThingConstants.ENTITY_TYPE, thingIdCache);
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
                            entityIdOptional.map(Objects::toString).orElse(""), signal);
            throw new IllegalArgumentException(message);
        } else {
            return cacheOptional.get().get(EnforcementCacheKey.of(entityIdOptional.get()))
                    .thenApply(entryOptional -> entryOptional.map(Entry::exists).orElse(false));
        }
    }

}
