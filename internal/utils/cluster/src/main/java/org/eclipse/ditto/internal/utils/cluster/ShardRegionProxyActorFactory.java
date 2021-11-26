/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.internal.utils.cluster.config.ClusterConfig;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.sharding.ClusterSharding;

/**
 * Factory for creating shard region proxy actors.
 *
 * @since 2.3.0
 */
@NotThreadSafe
public final class ShardRegionProxyActorFactory {

    private final ShardRegionExtractor extractor;
    private final ClusterSharding clusterSharding;

    private ShardRegionProxyActorFactory(final ShardRegionExtractor extractor, final ClusterSharding clusterSharding) {
        this.extractor = extractor;
        this.clusterSharding = clusterSharding;
    }

    /**
     * Returns a new instance of {@code ShardRegionFactory}.
     *
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ShardRegionProxyActorFactory newInstance(final ActorSystem actorSystem,
            final ClusterConfig clusterConfig) {

        ConditionChecker.checkNotNull(actorSystem, "actorSystem");
        ConditionChecker.checkNotNull(clusterConfig, "clusterConfig");

        return new ShardRegionProxyActorFactory(ShardRegionExtractor.of(clusterConfig.getNumberOfShards(), actorSystem),
                ClusterSharding.get(actorSystem));
    }

    /**
     * Starts a proxy of a shard region specified by the cluster role and shard region name arguments.
     * The actor reference is not being cached.
     *
     * @param shardRegionName name of the shard region.
     * @param clusterRole role of cluster members where the shard region resides.
     * @return reference of the shard region proxy.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if any argument is empty.
     */
    public ActorRef getShardRegionProxyActor(final CharSequence clusterRole, final CharSequence shardRegionName) {
        ConditionChecker.argumentNotEmpty(clusterRole, "clusterRole");
        ConditionChecker.argumentNotEmpty(shardRegionName, "shardRegionName");

        return clusterSharding.startProxy(shardRegionName.toString(), Optional.of(clusterRole.toString()), extractor);
    }

}
