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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.utils.pubsub.ddata.AbstractSubscriptions;
import org.eclipse.ditto.services.utils.pubsub.ddata.SubscriberData;
import org.eclipse.ditto.services.utils.pubsub.ddata.TopicData;

import akka.actor.ActorRef;

/**
 * Local subscriptions for distribution of subscribed topics as hash code sequences.
 */
@NotThreadSafe
public final class LiteralSubscriptions extends AbstractSubscriptions<String, String, LiteralUpdate> {

    private final LiteralUpdate updates;

    private LiteralSubscriptions(
            final Map<ActorRef, SubscriberData> subscriberDataMap,
            final Map<String, TopicData> topicToData,
            final LiteralUpdate updates) {
        super(subscriberDataMap, topicToData);
        this.updates = updates;
    }

    /**
     * Create a new subscriptions object.
     *
     * @return the subscriptions object.
     */
    public static LiteralSubscriptions newInstance() {
        return new LiteralSubscriptions(new HashMap<>(), new HashMap<>(), LiteralUpdate.empty());
    }

    @Override
    public LiteralUpdate export() {
        return LiteralUpdate.replaceAll(topicDataMap.keySet());
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
