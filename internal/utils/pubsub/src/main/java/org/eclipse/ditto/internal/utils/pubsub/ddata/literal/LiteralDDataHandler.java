/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsub.ddata.literal;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.eclipse.ditto.internal.utils.ddata.DistributedDataConfig;
import org.eclipse.ditto.internal.utils.pubsub.ddata.AbstractDDataHandler;

import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.cluster.ddata.Replicator;

/**
 * A distributed collection of strings indexed by ActorRef.
 */
public final class LiteralDDataHandler extends AbstractDDataHandler<Address, String, LiteralUpdate> {

    private LiteralDDataHandler(final DistributedDataConfig config,
            final ActorRefFactory actorRefFactory,
            final ActorSystem actorSystem,
            final Executor ddataExecutor,
            final String topicType) {
        super(config, actorRefFactory, actorSystem, ddataExecutor, topicType);
    }

    /**
     * Start distributed-data replicator for literal topics under an actor system's user guardian using the default
     * dispatcher.
     *
     * @param system the actor system.
     * @param ddataConfig the distributed data config.
     * @param topicType the type of messages, typically "message-type-name-aware".
     * @return access to the distributed data.
     */
    public static LiteralDDataHandler create(final ActorSystem system,
            final DistributedDataConfig ddataConfig,
            final String topicType) {

        return new LiteralDDataHandler(ddataConfig, system, system, system.dispatcher(), topicType);
    }

    @Override
    public long approximate(final String topic) {
        return topic.hashCode();
    }

    @Override
    public CompletionStage<Void> removeAddress(final Address address,
            final Replicator.WriteConsistency writeConsistency) {
        return update(getKey(address), writeConsistency, mmap -> mmap.remove(selfUniqueAddress, address));
    }
}
