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
package org.eclipse.ditto.internal.utils.cluster;

import javax.annotation.concurrent.NotThreadSafe;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.sharding.ShardRegion;

/**
 * Factory for creating shard regions.
 *
 * @since 3.0.0
 */
@NotThreadSafe
public final class ShardRegionCreator {

    private ShardRegionCreator() {
        throw new AssertionError();
    }

    /**
     * Create a shard region using a custom hand-off message.
     *
     * @param system The actor system.
     * @param shardRegionName The shard region name.
     * @param props Props of the sharded actors.
     * @param numberOfShards The number of shards.
     * @param clusterRole The cluster role whose members will start this shard region.
     * @return actor reference of the started shard region.
     */
    public static ActorRef start(final ActorSystem system, final String shardRegionName,
            final Props props, final int numberOfShards, final String clusterRole) {
        final var extractor = ShardRegionExtractor.of(numberOfShards, system);

        return start(system, shardRegionName, props, extractor, clusterRole);
    }

    /**
     * Create a shard region using a custom hand-off message.
     *
     * @param system The actor system.
     * @param shardRegionName The shard region name.
     * @param props Props of the sharded actors.
     * @param extractor The shard region extractor.
     * @param clusterRole The cluster role whose members will start this shard region.
     * @return actor reference of the started shard region.
     */
    public static ActorRef start(final ActorSystem system, final String shardRegionName, final Props props,
            final ShardRegion.MessageExtractor extractor, final String clusterRole) {
        final var clusterSharding = ClusterSharding.get(system);
        final var settings = ClusterShardingSettings.create(system)
                .withRole(clusterRole)
                .withPassivationStrategy(ClusterShardingSettings.PassivationStrategySettings$.MODULE$.disabled());
        final var strategy = clusterSharding.defaultShardAllocationStrategy(settings);

        return clusterSharding.start(shardRegionName, props, settings, extractor, strategy, new StopShardedActor());
    }

}
