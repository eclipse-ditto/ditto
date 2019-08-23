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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.NotThreadSafe;

import akka.actor.ActorRef;

/**
 * Consistence-maintenance part of all subscriptions.
 *
 * @param <H> type of hashes of a topic.
 * @param <T> type of approximations of subscriptions for distributed update.
 */
@NotThreadSafe
public abstract class AbstractSubscriptions<H, T> implements Subscriptions<T> {

    /**
     * Map from local subscribers to topics they subscribe to.
     */
    protected final Map<ActorRef, Set<String>> subscriberToTopic;

    /**
     * Map from local subscribers to their topic filters.
     */
    protected final Map<ActorRef, Predicate<Collection<String>>> subscriberToFilter;

    /**
     * Map from topic to subscriber count and pre-computed hashes.
     */
    protected final Map<String, TopicData<H>> topicToData;

    /**
     * Construct subscriptions using the given maps.
     * Consistency between the maps is not checked.
     *
     * @param subscriberToTopic map from subscribers to topics.
     * @param subscriberToFilter map from subscribers to filters.
     * @param topicToData map from topics to their data.
     */
    protected AbstractSubscriptions(
            final Map<ActorRef, Set<String>> subscriberToTopic,
            final Map<ActorRef, Predicate<Collection<String>>> subscriberToFilter,
            final Map<String, TopicData<H>> topicToData) {
        this.subscriberToTopic = subscriberToTopic;
        this.subscriberToFilter = subscriberToFilter;
        this.topicToData = topicToData;
    }

    /**
     * Hash a topic.
     *
     * @param topic the topic.
     * @return the hash codes of the topic.
     */
    protected abstract H hashTopic(final String topic);

    /**
     * Callback on each new topic introduced into the subscriptions.
     *
     * @param newTopic the new topic.
     */
    protected abstract void onNewTopic(final TopicData<H> newTopic);

    /**
     * Callback on each topic removed from the subscriptions as a whole.
     *
     * @param removedTopic the new topic.
     */
    protected abstract void onRemovedTopic(final TopicData<H> removedTopic);

    @Override
    public boolean subscribe(final ActorRef subscriber,
            final Set<String> topics,
            final Predicate<Collection<String>> filter) {
        if (!topics.isEmpty()) {
            // box the 'changed' flag in an array so that it can be assigned inside a closure.
            final boolean[] changed = new boolean[1];

            // change filter.
            subscriberToFilter.compute(subscriber, (k, previousFilter) -> {
                changed[0] = previousFilter != filter;
                return filter;
            });

            // add topics to subscriber
            subscriberToTopic.merge(subscriber, topics, AbstractSubscriptions::unionSet);

            // add subscriber for each new topic; detect whether there is any change.
            for (final String topic : topics) {
                topicToData.compute(topic, (k, previousData) -> {
                    if (previousData == null) {
                        changed[0] = true;
                        final TopicData<H> newTopic = TopicData.firstSubscriber(subscriber, hashTopic(topic));
                        onNewTopic(newTopic);
                        return newTopic;
                    } else {
                        // no short-circuit evaluation for OR: subscriber should always be added.
                        changed[0] |= previousData.addSubscriber(subscriber);
                        return previousData;
                    }
                });
            }
            return changed[0];
        } else {
            // update filter if there are any existing topic subscribed
            if (subscriberToTopic.containsKey(subscriber)) {
                subscriberToFilter.put(subscriber, filter);
                return true;
            } else {
                return false;
            }
        }
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
            removeSubscriberForTopics(subscriber, removed);
            if (remaining.isEmpty()) {
                // subscriber is removed
                subscriberToFilter.remove(subscriber);
                return null;
            } else {
                return remaining;
            }
        });
        return changed[0];
    }

    @Override
    public boolean removeSubscriber(final ActorRef subscriber) {
        // box 'changed' flag in array for assignment inside closure
        final boolean[] changed = new boolean[1];
        subscriberToTopic.computeIfPresent(subscriber, (k, topics) -> {
            changed[0] = removeSubscriberForTopics(subscriber, topics);
            return null;
        });
        subscriberToFilter.remove(subscriber);
        return changed[0];
    }

    @Override
    public boolean contains(final ActorRef subscriber) {
        return subscriberToTopic.containsKey(subscriber);
    }

    @Override
    public int countTopics() {
        return topicToData.size();
    }

    @Override
    public SubscriptionsReader snapshot() {
        return SubscriptionsReader.of(exportTopicData(), exportSubscriberToFilter());
    }

    private Map<ActorRef, Predicate<Collection<String>>> exportSubscriberToFilter() {
        return Collections.unmodifiableMap(new HashMap<>(subscriberToFilter));
    }

    private Map<String, Set<ActorRef>> exportTopicData() {
        return Collections.unmodifiableMap(topicToData.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().exportSubscribers())));
    }

    private boolean removeSubscriberForTopics(final ActorRef subscriber, final Collection<String> topics) {
        // box 'changed' flag for assignment inside closure
        final boolean[] changed = new boolean[1];
        for (final String topic : topics) {
            topicToData.computeIfPresent(topic, (k, data) -> {
                changed[0] |= data.removeSubscriber(subscriber);
                if (data.isEmpty()) {
                    onRemovedTopic(data);
                    return null;
                } else {
                    return data;
                }
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
    public boolean equals(final Object other) {
        if (other instanceof AbstractSubscriptions) {
            final AbstractSubscriptions that = (AbstractSubscriptions) other;
            return subscriberToTopic.equals(that.subscriberToTopic) &&
                    subscriberToFilter.equals(that.subscriberToFilter) &&
                    topicToData.equals(that.topicToData);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(subscriberToTopic, subscriberToFilter, topicToData);
    }

}

