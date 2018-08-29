/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.connectivity;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of {@link FilteredTopic}.
 */
@Immutable
public final class ImmutableFilteredTopic implements FilteredTopic {

    private static final String FILTER = "?filter=";

    private final Topic topic;
    @Nullable private final String filterString;

    private ImmutableFilteredTopic(final Topic topic, @Nullable final String filterString) {
        this.topic = topic;
        this.filterString = filterString;
    }

    /**
     * Creates a new {@code ImmutableFilteredTopic} instance.
     *
     * @param topic the topic of this filtered topic
     * @param filterString the optional RQL filter string of this topic
     * @return a new instance of ImmutableFilteredTopic
     */
    public static FilteredTopic of(final Topic topic, @Nullable final String filterString) {
        return new ImmutableFilteredTopic(topic, filterString);
    }

    /**
     * Creates a new {@code ImmutableFilteredTopic} instance.
     *
     * @param topicString the string representation of a topic
     * @return a new instance of ImmutableFilteredTopic
     */
    public static FilteredTopic fromString(final String topicString) {
        if (topicString.contains(FILTER)) {
            final String[] split = topicString.split("\\" + FILTER, 2);
            return new ImmutableFilteredTopic(Topic.forName(split[0])
                    .orElseThrow(() -> new IllegalArgumentException("Invalid topic: " + split[0])), split[1]);
        } else {
            return new ImmutableFilteredTopic(Topic.forName(topicString)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid topic: " + topicString)), null);
        }
    }

    @Override
    public Topic getTopic() {
        return topic;
    }

    @Override
    public Optional<String> getFilter() {
        return Optional.ofNullable(filterString);
    }

    @Override
    public int length() {
        return toString().length();
    }

    @Override
    public char charAt(final int index) {
        return toString().charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return toString().subSequence(start, end);
    }

    @Override
    public String toString() {
        if (filterString != null) {
            return topic.getName() + FILTER + filterString;
        } else {
            return topic.getName();
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableFilteredTopic)) {
            return false;
        }
        final ImmutableFilteredTopic that = (ImmutableFilteredTopic) o;
        return Objects.equals(topic, that.topic) &&
                Objects.equals(filterString, that.filterString);
    }

    @Override
    public int hashCode() {

        return Objects.hash(topic, filterString);
    }
}
