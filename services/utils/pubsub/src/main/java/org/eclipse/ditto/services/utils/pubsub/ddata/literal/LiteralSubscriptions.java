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
package org.eclipse.ditto.services.utils.pubsub.ddata.literal;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.utils.pubsub.ddata.AbstractSubscriptions;
import org.eclipse.ditto.services.utils.pubsub.ddata.TopicData;

import akka.actor.ActorRef;

/**
 * Local subscriptions for distribution of subscribed topics as hash code sequences.
 */
@NotThreadSafe
public final class LiteralSubscriptions extends AbstractSubscriptions<String, LiteralUpdate> {

    private final LiteralUpdate updates;

    private LiteralSubscriptions(
            final Map<ActorRef, Set<String>> subscriberToTopic,
            final Map<ActorRef, Predicate<Collection<String>>> subscriberToFilter,
            final Map<String, TopicData<String>> topicToData,
            final LiteralUpdate updates) {
        super(subscriberToTopic, subscriberToFilter, topicToData);
        this.updates = updates;
    }

    /**
     * Create a new subscriptions object.
     *
     * @return the subscriptions object.
     */
    public static LiteralSubscriptions newInstance() {
        return new LiteralSubscriptions(new HashMap<>(), new HashMap<>(), new HashMap<>(), LiteralUpdate.empty());
    }

    @Override
    protected String hashTopic(final String topic) {
        return topic;
    }

    @Override
    protected void onNewTopic(final TopicData<String> newTopic) {
        // nothing to do
    }

    @Override
    protected void onRemovedTopic(final TopicData<String> removedTopic) {
        // nothing to do
    }

    @Override
    public LiteralUpdate export(final boolean forceUpdate) {
        if (forceUpdate) {
            return LiteralUpdate.replaceAll(topicToData.keySet());
        } else {
            return updates.exportAndReset();
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof LiteralSubscriptions) {
            final LiteralSubscriptions that = (LiteralSubscriptions) other;
            return updates.equals(that.updates) && super.equals(other);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(updates, super.hashCode());
    }
}
