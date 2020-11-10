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
package org.eclipse.ditto.services.utils.pubsub.ddata.ack;

import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.eclipse.ditto.services.utils.ddata.DistributedDataConfig;
import org.eclipse.ditto.services.utils.pubsub.ddata.AbstractDDataHandler;

import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.cluster.ddata.Replicator;
import akka.japi.Pair;

// TODO: check serialization
// TODO: javadoc
public final class AckDDataHandler extends AbstractDDataHandler<Address, Pair<String, Set<String>>, AckDDataUpdate> {

    private AckDDataHandler(final DistributedDataConfig config,
            final ActorRefFactory actorRefFactory, final ActorSystem actorSystem,
            final Executor ddataExecutor, final String topicType) {
        super(config, actorRefFactory, actorSystem, ddataExecutor, topicType);
    }

    /**
     * Start distributed-data replicator for declared acknowledgement labels under an actor system's user guardian
     * using the default dispatcher.
     *
     * @param system the actor system.
     * @param ddataConfig the distributed data config.
     * @param topicType the type of messages, typically "message-type-name-aware".
     * @return access to the distributed data.
     */
    public static AckDDataHandler create(final ActorSystem system,
            final DistributedDataConfig ddataConfig,
            final String topicType) {

        return new AckDDataHandler(ddataConfig, system, system, system.dispatcher(), topicType);
    }

    @Override
    public CompletionStage<Void> removeAddress(final Address address,
            final Replicator.WriteConsistency writeConsistency) {
        return update(writeConsistency, mmap -> mmap.remove(selfUniqueAddress, address));
    }

    @Override
    public Pair<String, Set<String>> approximate(final String topic) {
        return Pair.create(null, Set.of(topic));
    }

}
