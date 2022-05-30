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
package org.eclipse.ditto.edge.service.dispatching;

import org.eclipse.ditto.internal.utils.cluster.ShardRegionProxyActorFactory;
import org.eclipse.ditto.internal.utils.cluster.config.ClusterConfig;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Create and retrieve shard region proxies.
 */
public final class ShardRegions {

    private static final String POLICIES_CLUSTER_ROLE = "policies";
    private static final String POLICIES_SHARD_REGION = "policy";

    private static final String THINGS_CLUSTER_ROLE = "things";
    private static final String THINGS_SHARD_REGION = "thing";

    private static final String SEARCH_CLUSTER_ROLE = "things-search";
    private static final String SEARCH_SHARD_REGION = "search-updater";

    private static final String CONNECTIVITY_CLUSTER_ROLE = "connectivity";
    private static final String CONNECTIVITY_SHARD_REGION = "connection";

    private final ShardRegionProxyActorFactory shardRegionProxyActorFactory;
    private final ActorRef policies;
    private final ActorRef things;
    private final ActorRef connections;
    private final ActorRef search;

    private ShardRegions(final ShardRegionProxyActorFactory shardRegionProxyActorFactory) {
        this.shardRegionProxyActorFactory = shardRegionProxyActorFactory;
        policies = startShardRegionProxy(POLICIES_CLUSTER_ROLE, POLICIES_SHARD_REGION);
        things = startShardRegionProxy(THINGS_CLUSTER_ROLE, THINGS_SHARD_REGION);
        connections = startShardRegionProxy(CONNECTIVITY_CLUSTER_ROLE, CONNECTIVITY_SHARD_REGION);
        search = startShardRegionProxy(SEARCH_CLUSTER_ROLE, SEARCH_SHARD_REGION);
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
