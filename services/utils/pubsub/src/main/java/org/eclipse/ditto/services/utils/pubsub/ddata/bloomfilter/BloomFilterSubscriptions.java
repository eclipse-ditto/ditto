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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.utils.pubsub.ddata.Hashes;
import org.eclipse.ditto.services.utils.pubsub.ddata.Subscriptions;
import org.eclipse.ditto.services.utils.pubsub.ddata.SubscriptionsReader;

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
public final class BloomFilterSubscriptions implements Hashes, SubscriptionsReader, Subscriptions<ByteString> {

    /**
     * Seeds of hash functions. They should be identical cluster-wide.
     */
    private final Collection<Integer> seeds;

    /**
     * Map from local subscribers to topics they subscribe to.
     */
    final Map<ActorRef, Set<String>> subscriberToTopic;

    /**
     * Map from topic to subscriber count and pre-computed hashes.
     */
    final Map<String, TopicData> topicToData;

    private BloomFilterSubscriptions(final Collection<Integer> seeds,
            final Map<ActorRef, Set<String>> subscriberToTopic,
            final Map<String, TopicData> topicToData) {
        this.seeds = seeds;
        this.subscriberToTopic = subscriberToTopic;
        this.topicToData = topicToData;
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
        return new BloomFilterSubscriptions(seeds, new HashMap<>(), new HashMap<>());
    }

    static BloomFilterSubscriptions of(final String seed, final int hashFamilySize) {
        final Collection<Integer> seeds = Hashes.digestStringsToIntegers(seed, hashFamilySize);
        return new BloomFilterSubscriptions(seeds, new HashMap<>(), new HashMap<>());
    }

    /**
     * Construct an immutable local-subscriptions object that always responds with the empty set when queried.
     *
     * @return an empty local-subscriptions object.
     */
    public static SubscriptionsReader empty() {
        return new BloomFilterSubscriptions(Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
    }

    @Override
    public Set<ActorRef> getSubscribers(final Collection<String> topics) {
        return topics.stream()
                .map(topicToData::get)
                .filter(Objects::nonNull)
                .flatMap(TopicData::streamSubscribers)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean subscribe(final ActorRef subscriber, final Set<String> topics) {
        if (!topics.isEmpty()) {
            // add topics to subscriber
            subscriberToTopic.merge(subscriber, topics, BloomFilterSubscriptions::unionSet);

            // add subscriber for each new topic; detect whether there is any change.
            // box the 'changed' flag in an array so that it can be assigned inside a closure.
            final boolean[] changed = new boolean[1];
            for (final String topic : topics) {
                topicToData.compute(topic, (k, previousData) -> {
                    if (previousData == null) {
                        changed[0] = true;
                        return TopicData.firstSubscriber(subscriber, getHashes(topic));
                    } else {
                        // no short-circuit evaluation for OR: subscriber should always be added.
                        changed[0] |= previousData.addSubscriber(subscriber);
                        return previousData;
                    }
                });
            }
            return changed[0];
        } else {
            return false;
        }
    }

    // convenience method to chain subscriptions.
    BloomFilterSubscriptions thenSubscribe(final ActorRef subscriber, final Set<String> topics) {
        subscribe(subscriber, topics);
        return this;
    }

    @Override
    public boolean unsubscribe(final ActorRef subscriber, final Set<String> topics) {
        // box 'changed' flag for assignment inside closure
        final boolean[] changed = new boolean[1];
        subscriberToTopic.computeIfPresent(subscriber, (k, previousTopics) -> {
            final List<String> removed = new ArrayList<>();
            final Set<String> remaining = new HashSet<>();
            for (final String topic : previousTopics) {
                if (topics.contains(topic)) {
                    removed.add(topic);
                } else {
                    remaining.add(topic);
                }
            }
            changed[0] = !removed.isEmpty();
            removeSubscribersForTopics(subscriber, removed);
            return remaining.isEmpty() ? null : remaining;
        });
        return changed[0];
    }

    @Override
    public boolean contains(final ActorRef subscriber) {
        return subscriberToTopic.containsKey(subscriber);
    }

    BloomFilterSubscriptions thenUnsubscribe(final ActorRef subscriber, final Set<String> topics) {
        unsubscribe(subscriber, topics);
        return this;
    }

    @Override
    public boolean removeSubscriber(final ActorRef subscriber) {
        // box 'changed' flag in array for assignment inside closure
        final boolean[] changed = new boolean[1];
        subscriberToTopic.computeIfPresent(subscriber, (k, topics) -> {
            changed[0] = removeSubscribersForTopics(subscriber, topics);
            return null;
        });
        return changed[0];
    }

    BloomFilterSubscriptions thenRemoveSubscriber(final ActorRef subscriber) {
        removeSubscriber(subscriber);
        return this;
    }

    /**
     * Get the size of hash functions {@code k} such that the false positive rate is {@code 1/2^k}.
     *
     * @return the negated false positive rate exponent.
     */
    private int getHashFamilySize() {
        return seeds.size();
    }

    @Override
    public int countTopics() {
        return topicToData.size();
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
                topicToData.values().stream().flatMap(TopicData::streamHashes));
    }

    @Override
    public SubscriptionsReader snapshot() {
        // while Scala's immutable collections are great for this use-case, I kept getting MethodNotFoundException
        // about $plus and $plus$plus. Copying everything instead.
        return new BloomFilterSubscriptions(
                Collections.unmodifiableList(new ArrayList<>(seeds)),
                Collections.unmodifiableMap(subscriberToTopic.entrySet()
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                entry -> Collections.unmodifiableSet(new HashSet<>(entry.getValue()))))),
                Collections.unmodifiableMap(topicToData.entrySet()
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().snapshot()))));
    }

    @Override
    public ByteString export() {
        return toOptimalBloomFilter();
    }

    private boolean removeSubscribersForTopics(final ActorRef subscriber, final Collection<String> topics) {
        // box 'changed' flag for assignment inside closure
        final boolean[] changed = new boolean[1];
        for (final String topic : topics) {
            topicToData.computeIfPresent(topic, (k, data) -> {
                changed[0] |= data.removeSubscriber(subscriber);
                return data.isEmpty() ? null : data;
            });
        }
        return changed[0];
    }

    private static Set<String> unionSet(final Set<String> s1, final Set<String> s2) {
        final Set<String> union = new HashSet<>(s1);
        union.addAll(s2);
        return union;
    }

    @Override
    public Collection<Integer> getSeeds() {
        return seeds;
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof BloomFilterSubscriptions) {
            final BloomFilterSubscriptions that = (BloomFilterSubscriptions) other;
            return seeds.equals(that.seeds) &&
                    subscriberToTopic.equals(that.subscriberToTopic) &&
                    topicToData.equals(that.topicToData);
        } else {
            return false;
        }
    }

    @NotThreadSafe
    private static final class TopicData {

        private final Set<ActorRef> subscribers;
        private final List<Integer> hashes;

        private TopicData(final Set<ActorRef> subscribers, final List<Integer> hashes) {
            this.subscribers = subscribers;
            this.hashes = hashes;
        }

        private boolean addSubscriber(final ActorRef newSubscriber) {
            return subscribers.add(newSubscriber);
        }

        private boolean removeSubscriber(final ActorRef subscriber) {
            return subscribers.remove(subscriber);
        }

        private boolean isEmpty() {
            return subscribers.isEmpty();
        }

        private Stream<Integer> streamHashes() {
            return hashes.stream();
        }

        private Stream<ActorRef> streamSubscribers() {
            return subscribers.stream();
        }

        private TopicData snapshot() {
            return new TopicData(Collections.unmodifiableSet(new HashSet<>(subscribers)),
                    Collections.unmodifiableList(new ArrayList<>(hashes)));
        }

        @Override
        public boolean equals(final Object other) {
            if (other instanceof TopicData) {
                final TopicData that = (TopicData) other;
                return subscribers.equals(that.subscribers) && hashes.equals(that.hashes);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(subscribers, hashes);
        }

        private static TopicData firstSubscriber(final ActorRef subscriber, final List<Integer> hashes) {
            final Set<ActorRef> subscribers = new HashSet<>();
            subscribers.add(subscriber);
            return new TopicData(subscribers, hashes);
        }
    }
}

