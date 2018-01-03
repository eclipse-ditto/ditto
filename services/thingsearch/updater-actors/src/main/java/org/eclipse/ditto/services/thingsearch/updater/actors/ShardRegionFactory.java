/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
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
    public ActorRef getSearchUpdaterShardRegion(final int numberOfShards, @Nonnull final Props thingUpdaterProps) {
        checkNotNull(thingUpdaterProps, "Props of ThingUpdater");

        final ClusterSharding clusterSharding = ClusterSharding.get(actorSystem);
        final ClusterShardingSettings shardingSettings = ClusterShardingSettings.create(actorSystem);
        final ShardRegionExtractor shardRegionExtractor = ShardRegionExtractor.of(numberOfShards, actorSystem);

        return clusterSharding.start(ThingsSearchConstants.SHARD_REGION, thingUpdaterProps, shardingSettings,
                shardRegionExtractor);
    }

}
