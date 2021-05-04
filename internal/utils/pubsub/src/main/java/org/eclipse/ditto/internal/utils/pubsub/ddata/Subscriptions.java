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
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import akka.actor.ActorRef;

/**
 * Write operations for a collection of local subscriptions.
 *
 * @param <T> representation of topics in the distributed data.
 */
public interface Subscriptions<T> {

    /**
     * Remove all known bindings from this data structure.
     */
    void clear();

    /**
     * Get the set of all known subscribers.
     *
     * @return the set of subscribers.
     */
    Set<ActorRef> getSubscribers();

    /**
     * Estimate the size of the subscription data in bytes in the distributed data.
     *
     * @return the estimated subscription data size in bytes.
     */
    long estimateSize();

    /**
     * Check if an actor subscribes to any topic.
     *
     * @param subscriber the actor.
     * @return whether it subscribes to any topic.
     */
    boolean contains(ActorRef subscriber);

    /**
     * Subscribe for filtered messages published at any of the given topics.
     *
     * @param subscriber the subscriber.
     * @param topics topics the subscriber subscribes to.
     * @param filter filter for topics of incoming messages associated with the subscriber.
     * @param group any group the subscriber belongs to, or null.
     * @return whether subscriptions changed.
     */
    boolean subscribe(ActorRef subscriber, Set<String> topics,
            @Nullable Predicate<Collection<String>> filter,
            @Nullable String group);

    /**
     * Subscribe for all messages published at any of the given topics.
     *
     * @param subscriber the subscriber.
     * @param topics topics the subscriber subscribes to.
     * @param group any group the subscriber belongs to, or null.
     * @return whether subscriptions changed.
     */
    default boolean subscribe(final ActorRef subscriber, final Set<String> topics, @Nullable final String group) {
        return subscribe(subscriber, topics, null, group);
    }

    /**
     * Unsubscribe to topics.
     *
     * @param subscriber the subscriber.
     * @param topics topics it unsubscribes from.
     * @return whether this object changed.
     */
    boolean unsubscribe(ActorRef subscriber, Set<String> topics);

    /**
     * Remove a subscriber and all its subscriptions.
     *
     * @param subscriber the subscriber to remove.
     * @return whether this object changed.
     */
    boolean removeSubscriber(ActorRef subscriber);

    /**
     * Create an unmodifiable copy of this object to send to other actors.
     *
     * @return a snapshot of this object.
     */
    SubscriptionsReader snapshot();

    /**
     * Export approximation of subscription data to be broadcast into the cluster.
     *
     * @return Approximation of all topics with subscribers for distributed data.
     */
    T export();

    /**
     * @return whether there are no subscribers.
     */
    default boolean isEmpty() {
        return estimateSize() <= 0;
    }
}
