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
package org.eclipse.ditto.services.concierge.starter.proxy;

import java.util.Optional;

import org.eclipse.ditto.services.concierge.util.config.AbstractConciergeConfigReader;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.sharding.ClusterSharding;

/**
 * Abstract class whose implementations create a sharded {@code EnforcerActor}.
 */
public abstract class AbstractEnforcerActorFactory<C extends AbstractConciergeConfigReader> {

    /**
     * The dispatcher name of the Executor to use in order to perform asynchronous operations in enforcement.
     */
    protected static final String ENFORCER_DISPATCHER = "enforcer-dispatcher";

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
