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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

import akka.actor.ActorRef;

/**
 * Set of subscribers of a topic together with its hashes.
 */
@NotThreadSafe
public final class TopicData {

    private final Set<ActorRef> subscribers;

    private TopicData(final Set<ActorRef> subscribers) {
        this.subscribers = subscribers;
    }

    /**
     * Create topic data upon arrival of the first subscriber.
     *
     * @param subscriber the first subscriber.
     * @return the topic data.
     */
    public static TopicData firstSubscriber(final ActorRef subscriber) {
        final Set<ActorRef> subscribers = new HashSet<>();
        subscribers.add(subscriber);
        return new TopicData(subscribers);
    }

    /**
     * Add a subscriber to the topic.
     *
     * @param newSubscriber the new subscriber.
     * @return whether this object changed.
     */
    public boolean addSubscriber(final ActorRef newSubscriber) {
        return subscribers.add(newSubscriber);
    }

    /**
     * Remove a subscriber from the topic.
     *
     * @param subscriber who is being removed.
     * @return whether this object changed.
     */
    public boolean removeSubscriber(final ActorRef subscriber) {
        return subscribers.remove(subscriber);
    }

    /**
     * @return whether there are no subscribers.
     */
    public boolean isEmpty() {
        return subscribers.isEmpty();
    }

    /**
     * @return an unmodifiable copy of the set of subscribers.
     */
    public Set<ActorRef> exportSubscribers() {
        return Set.copyOf(subscribers);
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof TopicData) {
            final TopicData that = (TopicData) other;
            return subscribers.equals(that.subscribers);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(subscribers);
    }
}
