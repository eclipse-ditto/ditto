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
package org.eclipse.ditto.services.utils.pubsub.ddata.compressed;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.ditto.services.utils.pubsub.ddata.AbstractSubscriptions;
import org.eclipse.ditto.services.utils.pubsub.ddata.Hashes;
import org.eclipse.ditto.services.utils.pubsub.ddata.TopicData;

import akka.actor.ActorRef;
import akka.util.ByteString;

/**
 * Local subscriptions for distribution of subscribed topics as hash code sequences.
 */
public final class CompressedSubscriptions extends AbstractSubscriptions<ByteString, CompressedUpdate>
        implements Hashes {

    /**
     * Seeds of hash functions. They should be identical cluster-wide.
     */
    private final Collection<Integer> seeds;
    private final Map<ByteString, Integer> hashCodeToTopicCount;
    private final CompressedUpdate updates;

    private CompressedSubscriptions(
            final Collection<Integer> seeds,
            final Map<ActorRef, Set<String>> subscriberToTopic,
            final Map<ActorRef, Predicate<Collection<String>>> subscriberToFilter,
            final Map<String, TopicData<ByteString>> topicToData,
            final Map<ByteString, Integer> hashCodeToTopicCount,
            final CompressedUpdate updates) {
        super(subscriberToTopic, subscriberToFilter, topicToData);
        this.seeds = seeds;
        this.hashCodeToTopicCount = hashCodeToTopicCount;
        this.updates = updates;
    }

    /**
     * Create a new compressed subscriptions object.
     *
     * @param seeds seeds of the family of hash functions..
     * @return the compressed subscriptions object.
     */
    public static CompressedSubscriptions of(final Collection<Integer> seeds) {
        return new CompressedSubscriptions(seeds, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                CompressedUpdate.empty());
    }

    @Override
    protected ByteString hashTopic(final String topic) {
        return CompressedDDataHandler.hashCodesToByteString(getHashes(topic));
    }

    @Override
    protected void onNewTopic(final TopicData<ByteString> newTopic) {
        hashCodeToTopicCount.compute(newTopic.getHashes(), (hashes, count) -> {
            if (count == null) {
                updates.insert(hashes);
                return 1;
            } else {
                return count + 1;
            }
        });
    }

    @Override
    protected void onRemovedTopic(final TopicData<ByteString> removedTopic) {
        hashCodeToTopicCount.computeIfPresent(removedTopic.getHashes(), (hashes, count) -> {
            if (count > 1) {
                return count - 1;
            } else {
                updates.delete(hashes);
                return null;
            }
        });
    }

    @Override
    public Collection<Integer> getSeeds() {
        return seeds;
    }

    @Override
    public CompressedUpdate export(final boolean forceUpdate) {
        if (forceUpdate) {
            return CompressedUpdate.replaceAll(hashCodeToTopicCount.keySet());
        } else {
            return updates.exportAndReset();
        }
    }
}
