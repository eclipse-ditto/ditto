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
package org.eclipse.ditto.services.utils.pubsub.ddata;

import java.util.concurrent.CompletionStage;

import akka.actor.ActorRef;
import akka.actor.Address;
import akka.cluster.ddata.Replicator;

/**
 * Writer of a distributed subscriber-topic relation.
 *
 * @param <T> type of topic updates to perform in the distributed data.
 */
public interface DDataWriter<T> {

    /**
     * Associate a subscriber with a topic.
     *
     * @param ownSubscriber actor that manages local subscriptions for this cluster member.
     * @param topicUpdates representation of topic updates.
     * @param writeConsistency write consistency for the operation.
     * @return future that completes or fails according to the result of the operation.
     */
    CompletionStage<Void> put(ActorRef ownSubscriber, T topicUpdates, Replicator.WriteConsistency writeConsistency);

    /**
     * Remove a subscriber outright.
     *
     * @param subscriber the subscriber to remove.
     * @param writeConsistency write consistency for the operation.
     * @return future that completes or fails according to the result of the operation.
     */
    CompletionStage<Void> removeSubscriber(ActorRef subscriber, Replicator.WriteConsistency writeConsistency);

    /**
     * Remove all subscribers at an address from the ddata with write consistency local.
     *
     * @param address the address of the cluster member to be removed.
     * @param writeConsistency write consistency for the operation.
     * @return future that completes or fails according to the result of the operation.
     */
    CompletionStage<Void> removeAddress(Address address, Replicator.WriteConsistency writeConsistency);
}
