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
package org.eclipse.ditto.internal.utils.pubsub.actors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.pubsub.PubSubFactory;
import org.eclipse.ditto.internal.utils.pubsub.api.PublishSignal;
import org.eclipse.ditto.internal.utils.pubsub.ddata.SubscriptionsReader;
import org.eclipse.ditto.internal.utils.pubsub.ddata.ack.Grouped;

import akka.actor.ActorRef;
import akka.japi.Pair;

/**
 * Index for publishing to a set of subscribers with groups.
 *
 * @param <T> the type of topics.
 */
final class PublisherIndex<T> {

    private final Predicate<Collection<T>> constantTrue = topics -> true;

    private final Map<T, Map<ActorRef, Set<String>>> index;
    private final Map<ActorRef, Predicate<Collection<T>>> filterMap;

    PublisherIndex(final Map<T, Map<ActorRef, Set<String>>> index,
            final Map<ActorRef, Predicate<Collection<T>>> filterMap) {
        this.index = index;
        this.filterMap = filterMap;
    }

    static <T> PublisherIndex<T> empty() {
        return new PublisherIndex<>(Map.of(), Map.of());
    }

    static <T> PublisherIndex<T> fromMultipleIndexes(final Collection<PublisherIndex<T>> indexes) {
        final Map<T, Map<ActorRef, Set<String>>> combinedIndex = new HashMap<>();
        indexes.stream()
                .map(p -> p.index)
                .forEach(index -> index.forEach((t, map) ->
                                combinedIndex.compute(t, (theT, existingMap) -> {
                                    final Map<ActorRef, Set<String>> nonNullMap = existingMap == null ? new HashMap<>() :
                                            existingMap;
                                    nonNullMap.putAll(map);
                                    return nonNullMap;
                                })
                        )
                );

        return new PublisherIndex<>(combinedIndex, Map.of());
    }

    static PublisherIndex<Long> fromDeserializedMMap(final Map<ActorRef, List<Grouped<Long>>> mmap) {
        final Map<Long, Map<ActorRef, Set<String>>> index = new HashMap<>();
        mmap.forEach((subscriber, groupedList) ->
                groupedList.forEach(grouped -> grouped.getValues()
                        .forEach(computeIndex(index, subscriber, grouped.getGroup().orElse("")))
                ));

        return new PublisherIndex<>(index, Map.of());
    }

    static PublisherIndex<String> fromSubscriptionsReader(final SubscriptionsReader reader) {
        final Map<String, Map<ActorRef, Set<String>>> index = new HashMap<>();
        final Map<ActorRef, Predicate<Collection<String>>> filterMap = new HashMap<>();
        reader.getSubscriberDataMap().forEach((subscriber, data) -> {
            data.getFilter().ifPresent(filter -> filterMap.put(subscriber, filter));
            data.getTopics().forEach(computeIndex(index, subscriber, data.getGroup().orElse("")));
        });

        return new PublisherIndex<>(index, filterMap);
    }

    List<Pair<ActorRef, PublishSignal>> assignGroupsToSubscribers(final Signal<?> signal,
            final Collection<T> topics, final CharSequence groupIndexKey) {

        return assignGroupsToSubscribers(signal, topics, null, groupIndexKey);
    }

    List<Pair<ActorRef, PublishSignal>> assignGroupsToSubscribers(final Signal<?> signal,
            final Collection<T> topics,
            @Nullable final Map<String, Integer> chosenGroups,
            final CharSequence groupIndexKey) {
        final Map<String, List<ActorRef>> groupToSubscribers = new HashMap<>();
        final Map<ActorRef, Map<String, Integer>> subscriberToChosenGroups = new HashMap<>();
        // compute groupToSubscribers and allot subscribers with the empty group
        for (final T topic : topics) {
            index.getOrDefault(topic, Map.of()).forEach((subscriber, groups) -> {
                if (filterMap.getOrDefault(subscriber, constantTrue).test(topics)) {
                    for (final String group : groups) {
                        if (group.isEmpty()) {
                            subscriberToChosenGroups.putIfAbsent(subscriber, new HashMap<>());
                        } else if (chosenGroups == null || chosenGroups.containsKey(group)) {
                            groupToSubscribers.compute(group, (g, list) -> {
                                final List<ActorRef> nonNullList = list == null ? new ArrayList<>() : list;
                                nonNullList.add(subscriber);
                                return nonNullList;
                            });
                        }
                    }
                }
            });
        }
        // choose a subscriber for each group consistently according to the group index key
        final int groupIndexKeyHash = PubSubFactory.hashForPubSub(groupIndexKey);
        groupToSubscribers.forEach((group, subscribers) -> {
            subscribers.sort(ActorRef::compareTo);
            final int groupDivisor = chosenGroups == null ? 1 : Math.max(1, chosenGroups.get(group));
            final ActorRef chosenSubscriber = subscribers.get((groupIndexKeyHash / groupDivisor) % subscribers.size());
            subscriberToChosenGroups.compute(chosenSubscriber, (s, groups) -> {
                final Map<String, Integer> nonNullGroups = groups == null ? new HashMap<>() : groups;
                nonNullGroups.put(group, subscribers.size());
                return nonNullGroups;
            });
        });

        return subscriberToChosenGroups.entrySet()
                .stream()
                .map(entry -> Pair.create(entry.getKey(), PublishSignal.of(signal, entry.getValue(), groupIndexKey)))
                .toList();
    }

    private static <T> Consumer<T> computeIndex(final Map<T, Map<ActorRef, Set<String>>> index,
            final ActorRef subscriber, final String group) {
        return topic -> index.compute(topic, (v, map) -> {
            final Map<ActorRef, Set<String>> nonNullMap = map == null ? new HashMap<>() : map;
            nonNullMap.compute(subscriber, (s, groups) -> {
                final Set<String> nonNullGroups = groups == null ? new HashSet<>() : groups;
                nonNullGroups.add(group);
                return nonNullGroups;
            });
            return nonNullMap;
        });
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PublisherIndex<?> that = (PublisherIndex<?>) o;

        return index.equals(that.index) && filterMap.equals(that.filterMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, filterMap);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "index=" + index +
                ", filterMap=" + filterMap +
                "]";
    }
}
