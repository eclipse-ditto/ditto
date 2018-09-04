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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of {@link FilteredTopic}.
 */
@Immutable
public final class ImmutableFilteredTopic implements FilteredTopic {

    private static final String QUERY_DELIMITER = "?";
    private static final String QUERY_ARG_DELIMITER = "&";
    private static final String QUERY_ARG_VALUE_DELIMITER = "=";

    private static final String FILTER_ARG = "filter";
    private static final String NAMESPACES_ARG = "namespaces";

    private final Topic topic;
    private final List<String> namespaces;
    @Nullable private final String filterString;

    private ImmutableFilteredTopic(final Topic topic, final List<String> namespaces,
            @Nullable final String filterString) {
        this.topic = topic;
        this.namespaces = Collections.unmodifiableList(new ArrayList<>(namespaces));
        this.filterString = filterString;
    }

    /**
     * Creates a new {@code ImmutableFilteredTopic} instance.
     *
     * @param topic the topic of this filtered topic
     * @param namespaces the namespaces for which the filter should be applied - if empty, all namespaces are
     * considered
     * @param filterString the optional RQL filter string of this topic
     * @return a new instance of ImmutableFilteredTopic
     */
    public static FilteredTopic of(final Topic topic, final List<String> namespaces,
            @Nullable final String filterString) {
        return new ImmutableFilteredTopic(topic, namespaces, filterString);
    }

    /**
     * Creates a new {@code ImmutableFilteredTopic} instance.
     *
     * @param filteredTopicString the string representation of a FilteredTopic
     * @return a new instance of ImmutableFilteredTopic
     */
    public static FilteredTopic fromString(final String filteredTopicString) {

        if (filteredTopicString.contains(QUERY_DELIMITER)) {
            final String[] splitString = filteredTopicString.split("\\" + QUERY_DELIMITER, 2);

            final String topicString = splitString[0];
            final String queryParamsString = splitString[1];
            final Map<String, String> paramValues = Arrays.stream(queryParamsString.split(QUERY_ARG_DELIMITER))
                    .map(paramString -> paramString.split(QUERY_ARG_VALUE_DELIMITER, 2))
                    .filter(av -> av.length == 2)
                    .collect(Collectors.toMap(av -> urlDecode(av[0]), av -> urlDecode(av[1])));

            final List<String> namespaces = Optional.ofNullable(paramValues.get(NAMESPACES_ARG))
                    .map(namespacesStr -> namespacesStr.split(","))
                    .map(Arrays::stream)
                    .orElse(Stream.empty())
                    .collect(Collectors.toList());

            final String filter = paramValues.get(FILTER_ARG);

            return new ImmutableFilteredTopic(Topic.forName(topicString).orElseThrow(() ->
                    TopicParseException.newBuilder(filteredTopicString,
                            "Unknown topic: " + topicString)
                            .build()
            ), namespaces, filter);
        } else {
            return new ImmutableFilteredTopic(Topic.forName(filteredTopicString).orElseThrow(() ->
                    TopicParseException.newBuilder(filteredTopicString,
                            "Unknown topic: " + filteredTopicString)
                        .build()
            ), Collections.emptyList(), null);
        }
    }

    private static String urlDecode(final String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (final UnsupportedEncodingException e) {
            return URLDecoder.decode(value);
        }
    }

    @Override
    public Topic getTopic() {
        return topic;
    }

    @Override
    public List<String> getNamespaces() {
        return namespaces;
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
        final String commaDelimitedNamespaces = String.join(",", namespaces);
        if (filterString != null && namespaces.isEmpty()) {
            return topic.getName() +
                    QUERY_DELIMITER + FILTER_ARG + QUERY_ARG_VALUE_DELIMITER + filterString;
        } else if (filterString != null) {
            return topic.getName() +
                    QUERY_DELIMITER + NAMESPACES_ARG + QUERY_ARG_VALUE_DELIMITER + commaDelimitedNamespaces +
                    QUERY_ARG_DELIMITER + FILTER_ARG + QUERY_ARG_VALUE_DELIMITER + filterString;
        } else if (!namespaces.isEmpty()) {
            return topic.getName() +
                    QUERY_DELIMITER + NAMESPACES_ARG + QUERY_ARG_VALUE_DELIMITER + commaDelimitedNamespaces;
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
                Objects.equals(namespaces, that.namespaces) &&
                Objects.equals(filterString, that.filterString);
    }

    @Override
    public int hashCode() {

        return Objects.hash(topic, namespaces, filterString);
    }
}
