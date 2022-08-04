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
package org.eclipse.ditto.things.service.enforcement.pre;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionProxyActorFactory;
import org.eclipse.ditto.internal.utils.cluster.config.DefaultClusterConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.policies.enforcement.config.DefaultEnforcementConfig;
import org.eclipse.ditto.policies.enforcement.config.EnforcementConfig;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.api.ThingsMessagingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * checks for the existence of things.
 */
final class ThingExistenceChecker {

    public static final String ENFORCEMENT_CACHE_DISPATCHER = "enforcement-cache-dispatcher";

    private final AsyncCacheLoader<ThingId, Entry<PolicyId>> thingIdLoader;
    private final ActorSystem actorSystem;

    ThingExistenceChecker(final ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
        final var enforcementConfig = DefaultEnforcementConfig.of(
                DefaultScopedConfig.dittoScoped(actorSystem.settings().config()));
        thingIdLoader = getThingIdLoader(actorSystem, enforcementConfig);
    }

    private AsyncCacheLoader<ThingId, Entry<PolicyId>> getThingIdLoader(
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

    public CompletionStage<Boolean> checkExistence(final ModifyThing signal) {
        try {
            return thingIdLoader.asyncLoad(signal.getEntityId(),
                            actorSystem.dispatchers().lookup(ENFORCEMENT_CACHE_DISPATCHER))
                    .thenApply(Entry::exists);
        } catch (final Exception e) {
            throw new IllegalStateException("Could not load thing via thingIdCacheLoader", e);
        }
    }

}
