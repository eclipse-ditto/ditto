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
package org.eclipse.ditto.services.utils.pubsub.bloomfilter;

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

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

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
public final class LocalSubscriptions implements Hashes {

    /**
     * Seeds of hash functions. They should be identical cluster-wide.
     */
    private final List<Integer> seeds;

    /**
     * Map from local subscribers to topics they subscribe to.
     */
    final Map<ActorRef, Set<String>> subscriberToTopic;

    /**
     * Map from topic to subscriber count and pre-computed hashes.
     */
    final Map<String, TopicData> topicToData;

    private LocalSubscriptions(final List<Integer> seeds,
            final Map<ActorRef, Set<String>> subscriberToTopic,
            final Map<String, TopicData> topicToData) {
        this.seeds = seeds;
        this.subscriberToTopic = subscriberToTopic;
        this.topicToData = topicToData;
    }

    /**
     * Construct a local-subscriptions object with a family of hash functions of the given size initiated with the given
     * seed. For the exported bloom filters to be meaningful on a remote node, all cluster members should have the same
     * seed.
     *
     * @param seed seed to initialize hash functions.
     * @param hashFamilySize the number {@code k} of hash functions to initialize. {@code k = -log_2(p)} where {@code p}
     * is the false-positive rate.
     * @return the local-subscriptions object.
     */
    public static LocalSubscriptions of(final String seed, final int hashFamilySize) {
        final List<Integer> seeds = Hashes.digestStringsToIntegers(seed, hashFamilySize);
        return new LocalSubscriptions(seeds, new HashMap<>(), new HashMap<>());
    }

    /**
     * Look up the set of subscribers subscribing to at least one of the given topics.
     *
     * @param topics the topics.
     * @return the set of subscribers.
     */
    public Set<ActorRef> getSubscribers(final Collection<String> topics) {
        return topics.stream()
                .map(topicToData::get)
                .filter(Objects::nonNull)
                .flatMap(TopicData::streamSubscribers)
                .collect(Collectors.toSet());
    }

    /**
     * Subscribe to topics.
     *
     * @param subscriber the subscriber.
     * @param topics topics the subscriber subscribes to.
     * @return this object.
     */
    public LocalSubscriptions subscribe(final ActorRef subscriber, final Set<String> topics) {
        if (!topics.isEmpty()) {
            // add topics to subscriber
            subscriberToTopic.merge(subscriber, topics, LocalSubscriptions::unionSet);

            // add subscriber for each new topic
            topics.forEach(topic ->
                    topicToData.compute(topic, (k, previousData) ->
                            previousData == null
                                    ? TopicData.firstSubscriber(subscriber, getHashes(topic))
                                    : previousData.addSubscriber(subscriber)
                    )
            );
        }
        return this;
    }

    /**
     * Unsubscribe to topics.
     *
     * @param subscriber the subscriber.
     * @param topics topics it unsubscribes from.
     * @return this object.
     */
    public LocalSubscriptions unsubscribe(final ActorRef subscriber, final Set<String> topics) {
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
            removeSubscribersForTopics(subscriber, removed);
            return remaining.isEmpty() ? null : remaining;
        });
        return this;
    }

    /**
     * Remove a subscriber and all its subscriptions.
     *
     * @param subscriber the subscriber to remove.
     */
    public LocalSubscriptions removeSubscriber(final ActorRef subscriber) {
        subscriberToTopic.computeIfPresent(subscriber, (k, topics) -> {
            removeSubscribersForTopics(subscriber, topics);
            return null;
        });
        return this;
    }

    /**
     * Get the size of hash functions {@code k} such that the false positive rate is {@code 1/2^k}.
     *
     * @return the negated false positive rate exponent.
     */
    public int getHashFamilySize() {
        return seeds.size();
    }

    /**
     * Get the total number of topics with active subscriptions.
     *
     * @return the number of topics.
     */
    public int getTopicCount() {
        return topicToData.size();
    }

    /**
     * Get the optimal number of bytes to allocate for the Bloom filter to maintain a decent false-positive rate.
     *
     * @param bufferFactor the ratio between the expected number of topics and the current number of topics.
     * @return optimal size of the Bloom filter in bytes.
     */
    public int getOptimalByteSize(final double bufferFactor) {
        final int n = getTopicCount();
        final int k = getHashFamilySize();
        final double ln2 = 1.44269504089; // rough approximate
        final int optimalBits = (int) Math.ceil(ln2 * bufferFactor * n * k);
        return Math.max(1, optimalBits / 8);
    }

    /**
     * Compute a Bloom filter of "optimal" size.
     *
     * @param bufferFactor the ratio between the expected number of topics and the current number of topics.
     * @return an optimal Bloom filter.
     */
    public ByteString toOptimalBloomFilter(final double bufferFactor) {
        return toBloomFilter(getOptimalByteSize(bufferFactor));
    }

    /**
     * Export this set to a bloom filter with a certain number of bytes.
     *
     * @param numberOfBytes the number of bytes in the bloom filter.
     * @return a Bloom filter with the given number of bytes.
     */
    public ByteString toBloomFilter(final int numberOfBytes) {
        return ByteStringAsBitSet.construct(numberOfBytes,
                topicToData.values().stream().flatMap(TopicData::streamHashes));
    }

    /**
     * Create an unmodifiable copy of this object to send to other actors.
     *
     * @return a snapshot of this object.
     */
    public LocalSubscriptions snapshot() {
        // while Scala's immutable collections are great for this use-case, I kept getting MethodNotFoundException
        // about $plus and $plus$plus. Copying everything instead.
        return new LocalSubscriptions(
                Collections.unmodifiableList(new ArrayList<>(seeds)),
                Collections.unmodifiableMap(subscriberToTopic.entrySet()
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                entry -> Collections.unmodifiableSet(new HashSet<>(entry.getValue()))))),
                Collections.unmodifiableMap(topicToData.entrySet()
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().snapshot()))));
    }

    private void removeSubscribersForTopics(final ActorRef subscriber, final Collection<String> topics) {
        topics.forEach(topic -> topicToData.computeIfPresent(topic, (k, data) -> data.removeSubscriber(subscriber)));
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
        if (other instanceof LocalSubscriptions) {
            final LocalSubscriptions that = (LocalSubscriptions) other;
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

        private TopicData addSubscriber(final ActorRef newSubscriber) {
            subscribers.add(newSubscriber);
            return this;
        }

        @Nullable
        private TopicData removeSubscriber(final ActorRef subscriber) {
            subscribers.remove(subscriber);
            return subscribers.size() >= 1 ? this : null;
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

