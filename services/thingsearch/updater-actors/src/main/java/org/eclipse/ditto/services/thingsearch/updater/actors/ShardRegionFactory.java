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
package org.eclipse.ditto.services.thingsearch.updater.actors;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.models.policies.PoliciesMessagingConstants;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;
import org.eclipse.ditto.services.models.thingsearch.ThingsSearchConstants;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;

/**
 * Factory for Shard Region {@link ActorRef}s of different services.
 */
@NotThreadSafe
final class ShardRegionFactory {

    static final String UPDATER_SHARD_REGION = ThingsSearchConstants.SHARD_REGION;

    private final ActorSystem actorSystem;

    private ShardRegionFactory(final ActorSystem theActorSystem) {
        actorSystem = theActorSystem;
    }

    /**
     * Returns an instance of {@code ShardRegionFactory} for the given ActorSystem.
     *
     * @param actorSystem the actor system for registering the cluster sharding.
     * @return the instance.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    @Nonnull
    public static ShardRegionFactory getInstance(@Nonnull final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "Actor system");
        return new ShardRegionFactory(actorSystem);
    }

    /**
     * Returns a new Sharding Region for Things.
     *
     * @param numberOfShards the number of shards to use.
     * @return the Sharding Region.
     */
    @Nonnull
    public ActorRef getThingsShardRegion(final int numberOfShards) {
        return createShardRegion(ThingsMessagingConstants.SHARD_REGION, ThingsMessagingConstants.CLUSTER_ROLE,
                numberOfShards);
    }

    /**
     * Returns a new Sharding Region for Policies.
     *
     * @param numberOfShards the number of shards to use.
     * @return the Sharding Region.
     */
    @Nonnull
    public ActorRef getPoliciesShardRegion(final int numberOfShards) {
        return createShardRegion(PoliciesMessagingConstants.SHARD_REGION, PoliciesMessagingConstants.CLUSTER_ROLE,
                numberOfShards);
    }

    private ActorRef createShardRegion(final String shardRegion, final String clusterRole, final int numberOfShards) {
        final ClusterSharding clusterSharding = ClusterSharding.get(actorSystem);
        final ShardRegionExtractor shardRegionExtractor = ShardRegionExtractor.of(numberOfShards, actorSystem);
        return clusterSharding.startProxy(shardRegion, Optional.of(clusterRole), shardRegionExtractor);
    }

    /**
     * Returns a new Sharding Region for the Search Updater.
     *
     * @param numberOfShards the number of shards to use.
     * @param thingUpdaterProps the Props of the ThingUpdater actor.
     * @return the Sharding Region.
     * @throws NullPointerException if {@code thingUpdaterProps} is {@code null}.
     */
    @Nonnull
    public ActorRef getSearchUpdaterShardRegion(final int numberOfShards,
            @Nonnull final Props thingUpdaterProps,
            final String clusterRole) {
        checkNotNull(thingUpdaterProps, "Props of ThingUpdater");

        final ClusterSharding clusterSharding = ClusterSharding.get(actorSystem);
        final ClusterShardingSettings shardingSettings =
                ClusterShardingSettings.create(actorSystem).withRole(clusterRole);
        final ShardRegionExtractor shardRegionExtractor = ShardRegionExtractor.of(numberOfShards, actorSystem);

        return clusterSharding.start(UPDATER_SHARD_REGION, thingUpdaterProps, shardingSettings, shardRegionExtractor);
    }

}
