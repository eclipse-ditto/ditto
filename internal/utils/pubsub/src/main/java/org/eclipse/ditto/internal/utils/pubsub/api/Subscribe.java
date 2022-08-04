/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsub.api;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import akka.actor.ActorRef;

/**
 * Request to subscribe to topics.
 */
public final class Subscribe extends AbstractRequest {

    @Nullable private final Predicate<Collection<String>> filter;
    @Nullable private final String group;
    private final boolean resubscribe;

    private Subscribe(final Collection<String> topics,
            final ActorRef subscriber,
            final boolean acknowledge,
            @Nullable final Predicate<Collection<String>> filter,
            @Nullable final String group,
            final boolean resubscribe) {
        super(topics, subscriber, acknowledge);
        this.filter = filter;
        this.group = group;
        this.resubscribe = resubscribe;
    }

    /**
     * Create a "subscribe" request.
     *
     * @param topics the topics to subscribe to.
     * @param subscriber who is subscribing.
     * @param acknowledge whether acknowledgement is desired.
     * @param group any group the subscriber belongs to, or null.
     * @return the request.
     */
    public static Subscribe of(final Collection<String> topics,
            final ActorRef subscriber,
            final boolean acknowledge,
            @Nullable final String group) {
        return new Subscribe(topics, subscriber, acknowledge, null, group, false);
    }

    /**
     * Create a "subscribe" request.
     *
     * @param topics the topics to subscribe to.
     * @param subscriber who is subscribing.
     * @param acknowledge whether acknowledgement is desired.
     * @param filter local filter for incoming messages.
     * @param group any group the subscriber belongs to, or null.
     * @return the request.
     */
    public static Subscribe of(final Collection<String> topics,
            final ActorRef subscriber,
            final boolean acknowledge,
            @Nullable final Predicate<Collection<String>> filter,
            @Nullable final String group,
            final boolean resubscribe) {
        return new Subscribe(topics, subscriber, acknowledge, filter, group, resubscribe);
    }

    /**
     * @return Filter for incoming messages.
     */
    @Nullable
    public Predicate<Collection<String>> getFilter() {
        return filter;
    }

    /**
     * @return the group the subscriber belongs to, or an empty optional.
     */
    public Optional<String> getGroup() {
        return Optional.ofNullable(group);
    }

    /**
     * @return Whether this is a resubscribe request.
     */
    public boolean isResubscribe() {
        return resubscribe;
    }

}
