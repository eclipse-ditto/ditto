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
 * TODO: document
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

    public static ShardRegions of(final ActorSystem actorSystem, final ClusterConfig clusterConfig) {
        return new ShardRegions(actorSystem, clusterConfig);
    }

    public ActorRef policies() {
        return policies;
    }

    public ActorRef things() {
        return things;
    }

    public ActorRef connections() {
        return connections;
    }

    public ActorRef startProxy(final String clusterRole, final String shardRegionName) {
        return startShardRegionProxy(clusterSharding, extractor, clusterRole, shardRegionName);
    }

    private static ActorRef startShardRegionProxy(final ClusterSharding clusterSharding,
            final ShardRegionExtractor extractor, final String clusterRole, final String shardRegionName) {

        return clusterSharding.startProxy(shardRegionName, Optional.of(clusterRole), extractor);
    }
}
