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
package org.eclipse.ditto.concierge.service.starter.proxy;

import org.eclipse.ditto.concierge.service.starter.ConciergeConfig;
import org.eclipse.ditto.edge.api.dispatching.ShardRegions;

import akka.actor.ActorContext;
import akka.actor.ActorRef;

/**
 * Abstract class whose implementations create a sharded {@code EnforcerActor}.
 */
@FunctionalInterface
public interface EnforcerActorFactory<C extends ConciergeConfig> {

    /**
     * Start the {@code EnforcerActor} and all dependent actors.
     *
     * @param context context in which to start actors other than shard regions and shard region proxies.
     * @param conciergeConfig the configuration of Concierge.
     * @param pubSubMediator Akka pub-sub mediator.
     * @param shardRegions shard regions.
     * @return actor reference to {@code EnforcerActor} shard region.
     */
    ActorRef startEnforcerActor(ActorContext context, C conciergeConfig, ActorRef pubSubMediator,
            ShardRegions shardRegions);

}
