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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import akka.actor.ActorRef;
import akka.japi.Pair;

/**
 * Consistence-maintenance part of all subscriptions.
 *
 * @param <R> type of representation of a topic in the distributed date.
 * @param <T> type of approximations of subscriptions for distributed update.
 */
@NotThreadSafe
public abstract class AbstractSubscriptions<R, T extends DDataUpdate<R>> implements Subscriptions<T> {

    /**
     * Map from local subscribers to topics they subscribe to.
     */
    protected final Map<ActorRef, SubscriberData> subscriberDataMap;

    /**
     * Map from topic to subscriber count and pre-computed hashes.
     */
    protected final Map<String, TopicData> topicDataMap;

    /**
     * Construct subscriptions using the given maps.
     * Consistency between the maps is not checked.
     *
     * @param subscriberDataMap map from subscribers to topics.
     * @param topicDataMap map from topics to their data.
     */
    protected AbstractSubscriptions(
            final Map<ActorRef, SubscriberData> subscriberDataMap,
            final Map<String, TopicData> topicDataMap) {
        this.subscriberDataMap = subscriberDataMap;
        this.topicDataMap = topicDataMap;
    }

    @Override
    public void clear() {
        subscriberDataMap.clear();
        topicDataMap.clear();
    }

    @Override
    public Set<ActorRef> getSubscribers() {
        return subscriberDataMap.keySet();
    }

    @Override
    public boolean subscribe(final ActorRef subscriber,
            final Set<String> topics,
            @Nullable final Predicate<Collection<String>> filter,
            @Nullable final String group) {
        if (!topics.isEmpty()) {
            // box the 'changed' flag in an array so that it can be assigned inside a closure.
            final var changed = new boolean[1];

            // add topics and filter.
            final var subscriberData = SubscriberData.of(topics, filter, group);
            subscriberDataMap.merge(subscriber, subscriberData, (oldData, newData) ->
                    newData.withTopics(unionSet(oldData.getTopics(), newData.getTopics())));

            // add subscriber for each new topic; detect whether there is any change.
            for (final String topic : topics) {
                topicDataMap.compute(topic, (k, previousData) -> {
                    if (previousData == null) {
                        changed[0] = true;
                        return TopicData.firstSubscriber(subscriber);
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
            return null != subscriberDataMap.computeIfPresent(subscriber, (k, data) -> data.withFilter(filter));
        }
    }

    @Override
    public boolean unsubscribe(final ActorRef subscriber, final Set<String> topics) {
        // box 'changed' flag for assignment inside closure
        final var changed = new boolean[1];
        subscriberDataMap.computeIfPresent(subscriber, (k, subscriberData) -> {
            final Set<String> previousTopics = subscriberData.getTopics();
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
                return null;
            } else {
                return subscriberData.withTopics(remaining);
            }
        });
        return changed[0];
    }

    @Override
    public boolean removeSubscriber(final ActorRef subscriber) {
        // box 'changed' flag in array for assignment inside closure
        final var changed = new boolean[1];
        subscriberDataMap.computeIfPresent(subscriber, (k, data) -> {
            changed[0] = removeSubscriberForTopics(subscriber, data.getTopics());
            return null;
        });
        return changed[0];
    }

    @Override
    public boolean contains(final ActorRef subscriber) {
        return subscriberDataMap.containsKey(subscriber);
    }

    @Override
    public SubscriptionsReader snapshot() {
        return SubscriptionsReader.fromSubscriberData(exportSubscriberData());
    }

    private Map<ActorRef, SubscriberData> exportSubscriberData() {
        return subscriberDataMap.entrySet()
                .stream()
                .map(entry -> Pair.create(entry.getKey(), entry.getValue().export()))
                .collect(Collectors.toMap(Pair::first, Pair::second));
    }

    private boolean removeSubscriberForTopics(final ActorRef subscriber, final Collection<String> topics) {
        // box 'changed' flag for assignment inside closure
        final var changed = new boolean[1];
        for (final String topic : topics) {
            topicDataMap.computeIfPresent(topic, (k, data) -> {
                changed[0] |= data.removeSubscriber(subscriber);
                if (data.isEmpty()) {
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
        if (other instanceof final AbstractSubscriptions<?, ?> that) {
            return subscriberDataMap.equals(that.subscriberDataMap) &&
                    topicDataMap.equals(that.topicDataMap);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(subscriberDataMap, topicDataMap);
    }

}
