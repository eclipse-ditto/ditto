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
package org.eclipse.ditto.internal.utils.pubsub.ddata;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Properties of a subscriber.
 */
@NotThreadSafe
public final class SubscriberData {

    private final Set<String> topics;
    @Nullable private final Predicate<Collection<String>> filter;
    @Nullable private final String group;

    private SubscriberData(final Set<String> topics,
            @Nullable final Predicate<Collection<String>> filter,
            @Nullable final String group) {
        this.topics = topics;
        this.filter = filter;
        this.group = group;
    }

    /**
     * Create subscriber data.
     *
     * @param topics topics the subscriber subscribes to.
     * @param filter topic filter of the subscriber.
     * @param group the group the subscriber belongs to, if any.
     * @return the subscriber data.
     */
    public static SubscriberData of(final Set<String> topics,
            @Nullable final Predicate<Collection<String>> filter,
            @Nullable final String group) {
        return new SubscriberData(topics, filter, group);
    }

    /**
     * Create an immutable copy of this object.
     *
     * @return the immutable copy.
     */
    public SubscriberData export() {
        return new SubscriberData(Set.copyOf(topics), filter, group);
    }

    /**
     * Create a copy of this object with topics replaced.
     *
     * @param topics the new topics.
     * @return the new subscriber data.
     */
    public SubscriberData withTopics(final Set<String> topics) {
        return new SubscriberData(topics, filter, group);
    }

    /**
     * Create a copy of this object with filter replaced.
     *
     * @param filter the new filter.
     * @return the new subscriber data.
     */
    public SubscriberData withFilter(@Nullable final Predicate<Collection<String>> filter) {
        return new SubscriberData(topics, filter, group);
    }

    /**
     * @return topics the subscriber subscribes to.
     */
    public Set<String> getTopics() {
        return topics;
    }

    /**
     * @return the filter of the subscriber.
     */
    public Optional<Predicate<Collection<String>>> getFilter() {
        return Optional.ofNullable(filter);
    }

    /**
     * @return the group the subscriber belongs to, or an empty optional.
     */
    public Optional<String> getGroup() {
        return Optional.ofNullable(group);
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof SubscriberData) {
            final SubscriberData that = (SubscriberData) other;
            return Objects.equals(topics, that.topics) &&
                    Objects.equals(filter, that.filter) &&
                    Objects.equals(group, that.group);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(topics, filter, group);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "topics=" + topics +
                ", filter=" + filter +
                ", group=" + group +
                "]";
    }
}
