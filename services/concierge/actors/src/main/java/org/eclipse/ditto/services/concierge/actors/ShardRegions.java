/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.concierge.actors;

import java.util.Optional;

import org.eclipse.ditto.services.models.connectivity.ConnectivityMessagingConstants;
import org.eclipse.ditto.services.models.policies.PoliciesMessagingConstants;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.services.utils.cluster.config.ClusterConfig;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.sharding.ClusterSharding;

/**
 * Create and retrieve shard region proxies.
 */
public final class ShardRegions {

    private final ClusterSharding clusterSharding;
    private final ShardRegionExtractor extractor;
    private final ActorRef policies;
    private final ActorRef things;
    private final ActorRef connections;

    private ShardRegions(final ActorSystem system, final ClusterConfig clusterConfig) {
        clusterSharding = ClusterSharding.get(system);
        extractor = ShardRegionExtractor.of(clusterConfig.getNumberOfShards(), system);
        policies = startShardRegionProxy(clusterSharding, extractor, PoliciesMessagingConstants.CLUSTER_ROLE,
                PoliciesMessagingConstants.SHARD_REGION);

        things = startShardRegionProxy(clusterSharding, extractor, ThingsMessagingConstants.CLUSTER_ROLE,
                ThingsMessagingConstants.SHARD_REGION);

        connections = startShardRegionProxy(clusterSharding, extractor, ConnectivityMessagingConstants.CLUSTER_ROLE,
                ConnectivityMessagingConstants.SHARD_REGION);
    }

    /**
     * Create a set of shard region proxies
     *
     * @param actorSystem the actor system.
     * @param clusterConfig the cluster config of the actor system.
     * @return a new ShardRegions object.
     */
    public static ShardRegions of(final ActorSystem actorSystem, final ClusterConfig clusterConfig) {
        return new ShardRegions(actorSystem, clusterConfig);
    }

    /**
     * Return the policies shard region proxy.
     *
     * @return policies shard region proxy.
     */
    public ActorRef policies() {
        return policies;
    }

    /**
     * Return the things shard region proxy.
     *
     * @return things shard region proxy.
     */
    public ActorRef things() {
        return things;
    }

    /**
     * Return the connections shard region proxy.
     *
     * @return connections shard region proxy.
     */
    public ActorRef connections() {
        return connections;
    }

    /**
     * Start proxy of a shard region of one's choosing. The actor reference is not cached.
     *
     * @param shardRegionName name of the shard region.
     * @param clusterRole role of cluster members where the shard region resides.
     * @return reference of the shard region proxy.
     */
    public ActorRef startProxy(final String shardRegionName, final String clusterRole) {
        return startShardRegionProxy(clusterSharding, extractor, clusterRole, shardRegionName);
    }

    private static ActorRef startShardRegionProxy(final ClusterSharding clusterSharding,
            final ShardRegionExtractor extractor, final String clusterRole, final String shardRegionName) {

        return clusterSharding.startProxy(shardRegionName, Optional.of(clusterRole), extractor);
    }
}
