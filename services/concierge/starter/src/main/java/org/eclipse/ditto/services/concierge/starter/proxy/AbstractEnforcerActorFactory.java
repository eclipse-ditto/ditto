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
package org.eclipse.ditto.services.concierge.starter.proxy;

import java.util.Optional;

import org.eclipse.ditto.services.base.config.ClusterConfigReader;
import org.eclipse.ditto.services.concierge.util.config.AbstractConciergeConfigReader;
import org.eclipse.ditto.services.models.concierge.ConciergeMessagingConstants;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;

/**
 * Abstract class whose implementations create a sharded {@code EnforcerActor}.
 */
public abstract class AbstractEnforcerActorFactory<C extends AbstractConciergeConfigReader> {

    /**
     * Start a proxy to a shard region.
     *
     * @param actorSystem actor system to start the proxy in.
     * @param numberOfShards number of shards in the shard region.
     * @param shardRegionName name of the shard region.
     * @param clusterRole role of the shard region.
     * @return actor reference to the shard region proxy.
     */
    protected static ActorRef startProxy(final ActorSystem actorSystem,
            final int numberOfShards,
            final String shardRegionName,
            final String clusterRole) {

        final ShardRegionExtractor shardRegionExtractor = ShardRegionExtractor.of(numberOfShards, actorSystem);

        return ClusterSharding.get(actorSystem)
                .startProxy(shardRegionName, Optional.of(clusterRole), shardRegionExtractor);
    }

    /**
     * Start a shard region.
     *
     * @param actorSystem actor system to start the proxy in.
     * @param clusterConfigReader the cluster configuration
     * @param props props of actors to start in the shard.
     * @return actor reference to the shard region.
     */
    protected static ActorRef startShardRegion(final ActorSystem actorSystem,
            final ClusterConfigReader clusterConfigReader,
            final Props props) {

        final ClusterShardingSettings settings = ClusterShardingSettings.create(actorSystem)
                .withRole(ConciergeMessagingConstants.CLUSTER_ROLE);

        final ShardRegionExtractor extractor =
                ShardRegionExtractor.of(clusterConfigReader.numberOfShards(), actorSystem);

        return ClusterSharding.get(actorSystem)
                .start(ConciergeMessagingConstants.SHARD_REGION, props, settings, extractor);
    }

    /**
     * Start the {@code EnforcerActor} and all dependent actors.
     *
     * @param context context in which to start actors other than shard regions and shard region proxies.
     * @param configReader the configuration reader of Concierge service.
     * @param pubSubMediator Akka pub-sub mediator.
     * @return actor reference to {@code EnforcerActor} shard region.
     */
    public abstract ActorRef startEnforcerActor(ActorContext context, C configReader,
            ActorRef pubSubMediator);

}
