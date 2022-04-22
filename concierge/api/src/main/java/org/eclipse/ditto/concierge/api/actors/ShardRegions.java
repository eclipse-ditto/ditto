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
package org.eclipse.ditto.concierge.api.actors;

import org.eclipse.ditto.connectivity.api.ConnectivityMessagingConstants;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionProxyActorFactory;
import org.eclipse.ditto.internal.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.policies.api.PoliciesMessagingConstants;
import org.eclipse.ditto.things.api.ThingsMessagingConstants;
import org.eclipse.ditto.thingsearch.api.ThingsSearchConstants;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Create and retrieve shard region proxies.
 */
public final class ShardRegions {

    private final ShardRegionProxyActorFactory shardRegionProxyActorFactory;
    private final ActorRef policies;
    private final ActorRef things;
    private final ActorRef connections;
    private final ActorRef search;

    private ShardRegions(final ShardRegionProxyActorFactory shardRegionProxyActorFactory) {
        this.shardRegionProxyActorFactory = shardRegionProxyActorFactory;
        policies =
                startShardRegionProxy(PoliciesMessagingConstants.CLUSTER_ROLE, PoliciesMessagingConstants.SHARD_REGION);

        things = startShardRegionProxy(ThingsMessagingConstants.CLUSTER_ROLE, ThingsMessagingConstants.SHARD_REGION);

        connections = startShardRegionProxy(ConnectivityMessagingConstants.CLUSTER_ROLE,
                ConnectivityMessagingConstants.SHARD_REGION);

        search = startShardRegionProxy(ThingsSearchConstants.SHARD_REGION, ThingsSearchConstants.CLUSTER_ROLE);
    }

    private ActorRef startShardRegionProxy(final CharSequence clusterRole, final CharSequence shardRegionName) {
        return shardRegionProxyActorFactory.getShardRegionProxyActor(clusterRole, shardRegionName);
    }

    /**
     * Create a set of shard region proxies
     *
     * @param actorSystem the actor system.
     * @param clusterConfig the cluster config of the actor system.
     * @return a new ShardRegions object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ShardRegions of(final ActorSystem actorSystem, final ClusterConfig clusterConfig) {
        return new ShardRegions(ShardRegionProxyActorFactory.newInstance(actorSystem, clusterConfig));
    }

    /**
     * Return the policies' shard region proxy.
     *
     * @return policies shard region proxy.
     */
    public ActorRef policies() {
        return policies;
    }

    /**
     * Return the things' shard region proxy.
     *
     * @return things shard region proxy.
     */
    public ActorRef things() {
        return things;
    }

    /**
     * Return the connections' shard region proxy.
     *
     * @return connections shard region proxy.
     */
    public ActorRef connections() {
        return connections;
    }

    /**
     * Return the search shard region proxy.
     *
     * @return search shard region proxy.
     */
    public ActorRef search() {
        return search;
    }

}
