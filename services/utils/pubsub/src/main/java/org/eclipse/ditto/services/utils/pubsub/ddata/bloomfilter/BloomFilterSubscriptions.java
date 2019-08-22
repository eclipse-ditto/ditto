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
package org.eclipse.ditto.services.utils.pubsub.ddata.bloomfilter;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.utils.pubsub.ddata.AbstractSubscriptions;
import org.eclipse.ditto.services.utils.pubsub.ddata.Hashes;
import org.eclipse.ditto.services.utils.pubsub.ddata.TopicData;

import akka.actor.ActorRef;
import akka.util.ByteString;

/**
 * A collection of local actor-refs, their topics, and the hashes of each topic by a family of hash functions.
 * It provides these services:
 * <ol>
 * <li>subscribe an actor to a set of topics,</li>
 * <li>unsubscribe an actor from a set of topics,</li>
 * <li>remove an actor and all its subscriptions, and</li>
 * <li>export the collection of all topics into a Bloom filter for distribution in the cluster.</li>
 * </ol>
 * <p>
 * Caution: this object is not thread-safe. Make a copy before sending it to another actor.
 */
@NotThreadSafe
public final class BloomFilterSubscriptions extends AbstractSubscriptions<List<Integer>, ByteString> implements Hashes {

    /**
     * Seeds of hash functions. They should be identical cluster-wide.
     */
    private final Collection<Integer> seeds;


    private BloomFilterSubscriptions(final Collection<Integer> seeds,
            final Map<ActorRef, Set<String>> subscriberToTopic,
            final Map<ActorRef, Predicate<Collection<String>>> subscriberToFilter,
            final Map<String, TopicData<List<Integer>>> topicToData) {
        super(subscriberToTopic, subscriberToFilter, topicToData);
        this.seeds = seeds;
    }

    /**
     * Construct a local-subscriptions object with a family of hash functions of the given size initiated with the given
     * seeds. For the exported bloom filters to be meaningful on a remote node, all cluster members should have the same
     * seeds.
     *
     * @param seeds seeds to initialize hash functions.
     * @return the local-subscriptions object.
     */
    public static BloomFilterSubscriptions of(final Collection<Integer> seeds) {
        return new BloomFilterSubscriptions(seeds, new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    /**
     * Construct a local-subscriptions object with a family of hash functions of the given size initiated with the given
     * seeds. For the exported bloom filters to be meaningful on a remote node, all cluster members should have the same
     * seeds.
     *
     * @param seed seed to initialize the hash family.
     * @param hashFamilySize size of the hash family.
     * @return the local-subscriptions object.
     */
    public static BloomFilterSubscriptions of(final String seed, final int hashFamilySize) {
        final Collection<Integer> seeds = Hashes.digestStringsToIntegers(seed, hashFamilySize);
        return of(seeds);
    }

    @Override
    protected List<Integer> hashTopic(final String topic) {
        return getHashes(topic);
    }

    @Override
    protected void onNewTopic(final TopicData<List<Integer>> newTopic) {
        // do nothing
    }

    @Override
    protected void onRemovedTopic(final TopicData<List<Integer>> removedTopic) {
        // do nothing
    }

    /**
     * Get the size of hash functions {@code k} such that the false positive rate is {@code 1/2^k}.
     *
     * @return the negated false positive rate exponent.
     */
    private int getHashFamilySize() {
        return seeds.size();
    }

    /**
     * Get the optimal number of bytes to allocate for the Bloom filter to maintain a decent false-positive rate.
     *
     * @return optimal size of the Bloom filter in bytes.
     */
    private int getOptimalByteSize() {
        final int n = countTopics();
        final int k = getHashFamilySize();
        final double ln2 = 1.44269504089; // rough approximate
        final int optimalBits = (int) Math.ceil(ln2 * n * k);
        return Math.max(1, optimalBits / 8);
    }

    /**
     * Compute a Bloom filter of "optimal" size.
     *
     * @return an optimal Bloom filter.
     */
    private ByteString toOptimalBloomFilter() {
        return toBloomFilter(getOptimalByteSize());
    }

    /**
     * Export this set to a bloom filter with a certain number of bytes.
     *
     * @param numberOfBytes the number of bytes in the bloom filter.
     * @return a Bloom filter with the given number of bytes.
     */
    ByteString toBloomFilter(final int numberOfBytes) {
        return ByteStringAsBitSet.construct(numberOfBytes,
                topicToData.values().stream().map(TopicData::getHashes).flatMap(List::stream));
    }

    @Override
    public ByteString export(final boolean forceUpdate) {
        // all updates are force updates by nature.
        return toOptimalBloomFilter();
    }

    @Override
    public Collection<Integer> getSeeds() {
        return seeds;
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof BloomFilterSubscriptions) {
            final BloomFilterSubscriptions that = (BloomFilterSubscriptions) other;
            return seeds.equals(that.seeds) && super.equals(other);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(seeds, super.hashCode());
    }

}

