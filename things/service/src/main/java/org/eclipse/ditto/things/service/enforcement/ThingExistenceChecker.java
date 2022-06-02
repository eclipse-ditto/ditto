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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionProxyActorFactory;
import org.eclipse.ditto.internal.utils.cluster.config.DefaultClusterConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.policies.enforcement.config.DefaultEnforcementConfig;
import org.eclipse.ditto.policies.enforcement.config.EnforcementConfig;
import org.eclipse.ditto.policies.enforcement.pre_enforcement.ExistenceChecker;
import org.eclipse.ditto.things.api.ThingsMessagingConstants;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

public final class ThingExistenceChecker implements ExistenceChecker {

    public static final String ENFORCEMENT_CACHE_DISPATCHER = "enforcement-cache-dispatcher";

    private final AsyncCacheLoader<EnforcementCacheKey, Entry<EnforcementCacheKey>> thingIdLoader;
    private final ActorSystem actorSystem;

    public ThingExistenceChecker(final ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
        final var enforcementConfig = DefaultEnforcementConfig.of(
                DefaultScopedConfig.dittoScoped(actorSystem.settings().config()));
        thingIdLoader = getThingIdCache(actorSystem, enforcementConfig);
    }

    private AsyncCacheLoader<EnforcementCacheKey, Entry<EnforcementCacheKey>> getThingIdCache(
            final ActorSystem actorSystem,
            final EnforcementConfig enforcementConfig) {

        final var clusterConfig = DefaultClusterConfig.of(actorSystem.settings().config().getConfig("ditto.cluster"));
        final ShardRegionProxyActorFactory shardRegionProxyActorFactory =
                ShardRegionProxyActorFactory.newInstance(actorSystem, clusterConfig);

        final ActorRef thingsShardRegion = shardRegionProxyActorFactory.getShardRegionProxyActor(
                ThingsMessagingConstants.CLUSTER_ROLE, ThingsMessagingConstants.SHARD_REGION);
        return new PreEnforcementThingIdCacheLoader(enforcementConfig.getAskWithRetryConfig(),
                actorSystem.getScheduler(),
                thingsShardRegion);
    }

    @Override
    public CompletionStage<Boolean> checkExistence(final Signal<?> signal) {
        final Optional<EntityId> entityIdOptional = WithEntityId.getEntityIdOfType(EntityId.class, signal);

        try {
            return thingIdLoader.asyncLoad(EnforcementCacheKey.of(
                                    entityIdOptional.orElseThrow(() -> getWrongEntityException(entityIdOptional, signal))),
                            actorSystem.dispatchers().lookup(ENFORCEMENT_CACHE_DISPATCHER))
                    .thenApply(Entry::exists);
        } catch (final Exception e) {
            throw new IllegalStateException("Could not load thing via thingIdCacheLoader", e);
        }
    }

    private static IllegalArgumentException getWrongEntityException(final Optional<EntityId> entityIdOptional,
            final Signal<?> signal) {

        final String message =
                String.format("ExistenceChecker: empty ID <%s:%s> for signal <%s>",
                        entityIdOptional.map(EntityId::getEntityType).map(Objects::toString).orElse(""),
                        entityIdOptional.map(Objects::toString).orElse(""), signal);
        return new IllegalArgumentException(message);
    }

}
