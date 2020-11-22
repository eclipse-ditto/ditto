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
package org.eclipse.ditto.services.utils.pubsub.actors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.ditto.services.utils.pubsub.api.PublishSignal;
import org.eclipse.ditto.services.utils.pubsub.ddata.ack.Grouped;
import org.eclipse.ditto.signals.base.Signal;

import akka.actor.ActorRef;
import akka.japi.Pair;

/**
 * Index for publishing to a set of subscribers with groups.
 *
 * @param <T> the type of topics.
 */
final class PublisherIndex<T> {

    private final Map<T, Map<ActorRef, Set<String>>> index;

    private PublisherIndex(final Map<T, Map<ActorRef, Set<String>>> index) {
        this.index = index;
    }

    static <T> PublisherIndex<T> empty() {
        return new PublisherIndex<>(Map.of());
    }

    static PublisherIndex<Long> fromDeserializedMMap(final Map<ActorRef, List<Grouped<Long>>> mmap) {
        final Map<Long, Map<ActorRef, Set<String>>> result = new HashMap<>();
        mmap.forEach((subscriber, groupedList) -> groupedList.forEach(grouped -> {
            final String group = grouped.getGroup().orElse("");
            grouped.getValues().forEach(topic -> result.compute(topic, (v, map) -> {
                final Map<ActorRef, Set<String>> nonNullMap = map == null ? new HashMap<>() : map;
                nonNullMap.compute(subscriber, (s, groups) -> {
                    final Set<String> nonNullGroups = groups == null ? new HashSet<>() : groups;
                    nonNullGroups.add(group);
                    return nonNullGroups;
                });
                return nonNullMap;
            }));
        }));
        return new PublisherIndex<>(result);
    }

    List<Pair<ActorRef, PublishSignal>> allotGroupsToSubscribers(final Signal<?> signal, final Collection<T> topics) {
        final Map<String, List<ActorRef>> groupToSubscribers = new HashMap<>();
        final Map<ActorRef, Set<String>> subscriberToChosenGroups = new HashMap<>();
        // compute groupToSubscribers and allot subscribers with the empty group
        for (final T topic : topics) {
            index.getOrDefault(topic, Map.of()).forEach((subscriber, groups) -> {
                for (final String group : groups) {
                    if (group.isEmpty()) {
                        subscriberToChosenGroups.putIfAbsent(subscriber, new HashSet<>());
                    } else {
                        groupToSubscribers.compute(group, (g, list) -> {
                            final List<ActorRef> nonNullList = list == null ? new ArrayList<>() : list;
                            nonNullList.add(subscriber);
                            return nonNullList;
                        });
                    }
                }
            });
        }
        // choose a subscriber for each group consistently according to the entity ID of the signal
        // use string hashCode to guarantee the last byte to influence the subscriber selection
        // Math.max needed because Math.abs(Integer.MIN_VALUE) < 0
        final int entityIdHash = Math.max(0, Math.abs(signal.getEntityId().toString().hashCode()));
        groupToSubscribers.forEach((group, subscribers) -> {
            subscribers.sort(ActorRef::compareTo);
            final ActorRef chosenSubscriber = subscribers.get(entityIdHash % subscribers.size());
            subscriberToChosenGroups.compute(chosenSubscriber, (s, groups) -> {
                final Set<String> nonNullGroups = groups == null ? new HashSet<>() : groups;
                nonNullGroups.add(group);
                return nonNullGroups;
            });
        });

        return subscriberToChosenGroups.entrySet()
                .stream()
                .map(entry -> Pair.create(entry.getKey(), PublishSignal.of(signal, entry.getValue())))
                .collect(Collectors.toList());
    }

}
