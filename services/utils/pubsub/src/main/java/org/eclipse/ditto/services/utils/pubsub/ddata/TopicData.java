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

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.concurrent.NotThreadSafe;

import akka.actor.ActorRef;

/**
 * Set of subscribers of a topic together with its hashes.
 *
 * @param <H> representation of hashes of the topic.
 */
@NotThreadSafe
public final class TopicData<H> {

    private final Set<ActorRef> subscribers;
    private final H hashes;

    private TopicData(final Set<ActorRef> subscribers, final H hashes) {
        this.subscribers = subscribers;
        this.hashes = hashes;
    }

    /**
     * Create topic data upon arrival of the first subscriber.
     *
     * @param <H> type of hashes.
     * @param subscriber the first subscriber.
     * @param hashes hashes of the topic.
     * @return the topic data.
     */
    public static <H> TopicData<H> firstSubscriber(final ActorRef subscriber, final H hashes) {
        final Set<ActorRef> subscribers = new HashSet<>();
        subscribers.add(subscriber);
        return new TopicData<>(subscribers, hashes);
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
     * @return hash codes of the topic.
     */
    public H getHashes() {
        return hashes;
    }

    /**
     * @return a stream of subscribers of the topic.
     */
    public Stream<ActorRef> streamSubscribers() {
        return subscribers.stream();
    }

    /**
     * @return an unmodifiable copy of the set of subscribers.
     */
    public Set<ActorRef> exportSubscribers() {
        return Collections.unmodifiableSet(new HashSet<>(subscribers));
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
}
