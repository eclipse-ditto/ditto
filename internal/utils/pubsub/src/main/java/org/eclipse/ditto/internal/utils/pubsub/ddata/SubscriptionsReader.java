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

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import akka.actor.ActorRef;

/**
 * Reader of local subscriptions.
 */
public final class SubscriptionsReader {

    private final Map<ActorRef, SubscriberData> subscriberDataMap;

    private SubscriptionsReader(final Map<ActorRef, SubscriberData> subscriberDataMap) {
        this.subscriberDataMap = subscriberDataMap;
    }

    /**
     * @return An empty subscription-reader.
     */
    public static SubscriptionsReader empty() {
        return new SubscriptionsReader(Map.of());
    }

    /**
     * Construct a subscriptions-reader from immutable collections.
     *
     * @param subscriberDataMap a map from subscriber to its topics, group and filter.
     * @return a subscription-reader.
     */
    public static SubscriptionsReader fromSubscriberData(final Map<ActorRef, SubscriberData> subscriberDataMap) {
        return new SubscriptionsReader(subscriberDataMap);
    }

    /**
     * @return the ternary relation between subscribers, groups and topics.
     */
    public Map<ActorRef, SubscriberData> getSubscriberDataMap() {
        return subscriberDataMap;
    }

    /**
     * Look up the set of subscribers subscribing to at least one of the given topics.
     * NOT performant. Only intended for tests.
     *
     * @param topics the topics.
     * @return the set of subscribers.
     */
    public Set<ActorRef> getSubscribers(final Collection<String> topics) {
        return subscriberDataMap.entrySet()
                .stream()
                .filter(entry -> topics.stream().anyMatch(entry.getValue().getTopics()::contains))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof SubscriptionsReader) {
            final SubscriptionsReader that = (SubscriptionsReader) other;
            return subscriberDataMap.equals(that.subscriberDataMap);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return subscriberDataMap.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "subscriberDataMap=" + subscriberDataMap +
                "]";
    }
}
