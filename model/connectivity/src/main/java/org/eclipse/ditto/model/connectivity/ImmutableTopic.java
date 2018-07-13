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

/**
 * TODO TJ javadoc
 */
public final class ImmutableTopic implements Topic {

    private static final String FILTER = "?filter=";

    private final String path;
    @Nullable private final String filterString;

    private ImmutableTopic(final String path, @Nullable final String filterString) {
        this.path = path;
        this.filterString = filterString;
    }

    /**
     * Creates a new {@code ImmutableTopic} instance.
     *
     * @param path the path of this topic
     * @param filterString the optional RQL filter string of this topic
     * @return a new instance of ImmutableTopic
     */
    public static Topic of(final String path, @Nullable final String filterString) {
        return new ImmutableTopic(path, filterString);
    }

    /**
     * Creates a new {@code ImmutableTopic} instance.
     *
     * @param topicString the string representation of a topic
     * @return a new instance of ImmutableTopic
     */
    public static Topic fromString(final String topicString) {
        if (topicString.contains(FILTER)) {
            final String[] split = topicString.split("\\" + FILTER, 2);
            return new ImmutableTopic(split[0], split[1]);
        } else {
            return new ImmutableTopic(topicString, null);
        }
    }

    @Override
    public String getPath() {
        return path;
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
            return path + FILTER + filterString;
        } else {
            return path;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableTopic)) {
            return false;
        }
        final ImmutableTopic that = (ImmutableTopic) o;
        return Objects.equals(path, that.path) &&
                Objects.equals(filterString, that.filterString);
    }

    @Override
    public int hashCode() {

        return Objects.hash(path, filterString);
    }
}
