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
package org.eclipse.ditto.internal.utils.pubsub.ddata;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import akka.actor.ActorRef;
import akka.actor.Address;
import akka.cluster.ddata.Key;
import akka.cluster.ddata.ORMultiMap;
import akka.cluster.ddata.Replicator;

/**
 * Reader of distributed Bloom filters of subscribed topics.
 *
 * @param <K> type of keys of the multimap.
 * @param <T> type of topic approximations.
 */
public interface DDataReader<K, T> {

    /**
     * Map a topic to a key with which to read distributed data.
     *
     * @param topic the topic.
     * @return its approximation in the distributed data.
     */
    long approximate(String topic);

    /**
     * Start sending distributed data change events to the recipient.
     * No further events are sent once the recipient terminates.
     *
     * @param recipient the recipient of distributed data events.
     */
    void receiveChanges(ActorRef recipient);

    /**
     * Returns the number of shards Ditto's ddata extension applies for Map keys.
     *
     * @return the number of shards Ditto's ddata extension applies for Map keys
     */
    int getNumberOfShards();

    /**
     * Creates/gets a key for the passed {@code hashProvider} object.
     *
     * @param hashProvider the key used to calculate the number of the shard to append to the key.
     * @return Key of the distributed data.
     */
    default Key<ORMultiMap<K, T>> getKey(final Address hashProvider) {
        final int shardNumber = Math.abs(hashProvider.hashCode() % getNumberOfShards());
        return getKey(shardNumber);
    }

    /**
     * Creates/gets a key for the passed {@code shardNumber}.
     * Should only be directly called in order to iterate over all existing keys.
     *
     * @param shardNumber the number of the shard to append to the key.
     * @return Key of the distributed data.
     */
    Key<ORMultiMap<K, T>> getKey(int shardNumber);

    /**
     * Asynchronously retrieves the replicated data of a key.
     *
     * @param key the key to get the replicated data for.
     * @param consistency how many replicas to consult.
     * @return future of the replicated data that completes exceptionally on error.
     */
    CompletionStage<Optional<ORMultiMap<K, T>>> get(Key<ORMultiMap<K, T>> key, Replicator.ReadConsistency consistency);

    /**
     * Retrieve all replicated data shards.
     *
     * @param consistency the read consistency.
     * @return the list of replicated data.
     */
    CompletionStage<List<ORMultiMap<K, T>>> getAllShards(Replicator.ReadConsistency consistency);
}
