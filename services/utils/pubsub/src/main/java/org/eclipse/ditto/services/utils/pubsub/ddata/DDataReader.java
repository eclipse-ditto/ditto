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

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import akka.actor.ActorRef;
import akka.cluster.ddata.Key;
import akka.cluster.ddata.ORMultiMap;
import akka.cluster.ddata.Replicator;
import scala.collection.immutable.Set;

/**
 * Reader of distributed Bloom filters of subscribed topics.
 *
 * @param <T> type of topic approximations.
 */
public interface DDataReader<K, T> {

    /**
     * Get subscribers from a list of topic hashes.
     *
     * @param topicHashes the hash codes of each topic.
     * @return future collection of subscribers whose Bloom filter contains all hashes of 1 or more topics.
     */
    CompletionStage<Collection<K>> getSubscribers(Collection<T> topicHashes);

    /**
     * Read a low-level map from the local replicator.
     *
     * @return the low-level map.
     */
    default CompletionStage<Map<K, Set<T>>> read() {
        return read((Replicator.ReadConsistency) Replicator.readLocal());
    }

    /**
     * Read a low-level map from the local replicator.
     *
     * @return the low-level map.
     */
    CompletionStage<Map<K, Set<T>>> read(Replicator.ReadConsistency readConsistency);

    /**
     * Map a topic to a key with which to read distributed data.
     *
     * @param topic the topic.
     * @return its approximation in the distributed data.
     */
    T approximate(String topic);

    /**
     * Start sending distributed data change events to the recipient.
     * No further events are sent once the recipient terminates.
     *
     * @param recipient the recipient of distributed data events.
     */
    void receiveChanges(ActorRef recipient);

    /**
     * @return Key of the distributed data.
     */
    Key<ORMultiMap<K, T>> getKey();
}
