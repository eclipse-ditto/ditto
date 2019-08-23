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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import akka.actor.ActorRef;

/**
 * Reader of local subscriptions.
 */
@Immutable
public final class SubscriptionsReader {

    /**
     * Constant-true predicate as the default filter.
     */
    private static final Predicate<Collection<String>> CONSTANT_TRUE = topics -> true;
    
    private final Map<String, Set<ActorRef>> topicToSubscriber;
    private final Map<ActorRef, Predicate<Collection<String>>> subscriberToFilter;

    private SubscriptionsReader(
            final Map<String, Set<ActorRef>> topicToSubscriber,
            final Map<ActorRef, Predicate<Collection<String>>> subscriberToFilter) {

        this.topicToSubscriber = Collections.unmodifiableMap(new HashMap<>(topicToSubscriber));
        this.subscriberToFilter = Collections.unmodifiableMap(new HashMap<>(subscriberToFilter));
    }

    /**
     * @return An empty subscription-reader.
     */
    public static SubscriptionsReader empty() {
        return new SubscriptionsReader(Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * Construct a subscriptions-reader from immutable collections.
     *
     * @param topicToData relation between topics and subscribers.
     * @param subscriberToFilter relation between subscribers and their filters.
     * @return a subscription-reader.
     */
    public static SubscriptionsReader of(final Map<String, Set<ActorRef>> topicToData,
            final Map<ActorRef, Predicate<Collection<String>>> subscriberToFilter) {

        return new SubscriptionsReader(topicToData, subscriberToFilter);
    }

    /**
     * Look up the set of subscribers subscribing to at least one of the given topics.
     *
     * @param topics the topics.
     * @return the set of subscribers.
     */
    public Set<ActorRef> getSubscribers(final Collection<String> topics) {
        return topics.stream()
                .map(topicToSubscriber::get)
                .filter(Objects::nonNull)
                .flatMap(Set::stream)
                .collect(Collectors.toSet()) // deduplicate subscribers
                .stream()
                .filter(subscriber -> subscriberToFilter.getOrDefault(subscriber, CONSTANT_TRUE).test(topics))
                .collect(Collectors.toSet());
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof SubscriptionsReader) {
            final SubscriptionsReader that = (SubscriptionsReader) other;
            return topicToSubscriber.equals(that.topicToSubscriber) &&
                    subscriberToFilter.equals(that.subscriberToFilter);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(topicToSubscriber, subscriberToFilter);
    }
}
