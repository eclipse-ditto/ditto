/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.things.model.ThingFieldSelector;

/**
 * Immutable implementation of {@link FilteredTopic}.
 */
@Immutable
final class ImmutableFilteredTopic implements FilteredTopic {

    private static final String QUERY_DELIMITER = "?";
    private static final String QUERY_ARG_DELIMITER = "&";
    private static final String QUERY_ARG_VALUE_DELIMITER = "=";

    private static final String FILTER_ARG = "filter";
    private static final String NAMESPACES_ARG = "namespaces";
    private static final String EXTRA_FIELDS_ARG = "extraFields";


    private final Topic topic;
    private final List<String> namespaces;
    private final List<String> filters;
    @Nullable private final ThingFieldSelector extraFields;

    private ImmutableFilteredTopic(final ImmutableFilteredTopicBuilder builder) {
        topic = builder.topic;
        final Collection<String> namespacesFromBuilder = builder.namespaces;
        namespaces = null != namespacesFromBuilder
                ? Collections.unmodifiableList(new ArrayList<>(namespacesFromBuilder))
                : Collections.emptyList();
        final Collection<String> filtersFromBuilder = builder.filters;
        filters = null != filtersFromBuilder
                ? Collections.unmodifiableList(new ArrayList<>(filtersFromBuilder))
                : Collections.emptyList();
        extraFields = builder.extraFields;
    }

    /**
     * Returns a builder for creating an instance of ImmutableFilteredTopic.
     *
     * @param topic the topic of this filtered topic.
     * @return the builder.
     * @throws NullPointerException if {@code topic} is {@code null}.
     */
    public static ImmutableFilteredTopicBuilder getBuilder(final Topic topic) {
        return new ImmutableFilteredTopicBuilder(topic);
    }

    /**
     * Creates a new {@code ImmutableFilteredTopic} instance from the given string.
     *
     * @param filteredTopicString the string representation of a FilteredTopic.
     * @return instance.
     * @throws NullPointerException if {@code filteredTopicString} is {@code null}.
     * @throws org.eclipse.ditto.things.model.InvalidThingFieldSelectionException when the given
     * {@code filteredTopicString} contained a field selector with invalid fields.
     */
    public static ImmutableFilteredTopic fromString(final String filteredTopicString) {
        checkNotNull(filteredTopicString, "filteredTopicString");
        final FilteredTopicStringParser parser = new FilteredTopicStringParser(filteredTopicString);
        return parser.parse();
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
        return filters.isEmpty() ? Optional.empty() : Optional.of(filters.get(0));
    }

    @Override
    public List<String> getFilters() {
        return filters;
    }

    @Override
    public Optional<JsonFieldSelector> getExtraFields() {
        return Optional.ofNullable(extraFields);
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
        return join(QUERY_DELIMITER, topic.getName(), getQueryParametersAsString());
    }

    private String getQueryParametersAsString() {
        final List<String> queryParameterStrings = new ArrayList<>(2 + filters.size());
        queryParameterStrings.add(getQueryParameterString(NAMESPACES_ARG, String.join(",", namespaces)));
        for (final String filter : filters) {
            queryParameterStrings.add(getQueryParameterString(FILTER_ARG, filter));
        }
        queryParameterStrings.add(getQueryParameterString(EXTRA_FIELDS_ARG, extraFields));
        return join(QUERY_ARG_DELIMITER, queryParameterStrings.toArray(new String[0]));
    }

    private static String getQueryParameterString(final String parameterName, @Nullable final Object parameterValue) {
        if (null != parameterValue) {
            final String parameterValueAsString = parameterValue.toString();
            if (!parameterValueAsString.isEmpty()) {
                return parameterName + QUERY_ARG_VALUE_DELIMITER + parameterValueAsString;
            }
        }
        return "";
    }

    private static String join(final String delimiter, final String... elements) {
        final StringBuilder stringBuilder = new StringBuilder();
        String currentDelimiter = "";
        for (final String element : elements) {
            if (null != element && !element.isEmpty()) {
                stringBuilder.append(currentDelimiter).append(element);
                currentDelimiter = delimiter;
            }
        }
        return stringBuilder.toString();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableFilteredTopic that = (ImmutableFilteredTopic) o;
        return topic == that.topic &&
                namespaces.equals(that.namespaces) &&
                filters.equals(that.filters) &&
                Objects.equals(extraFields, that.extraFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topic, namespaces, filters, extraFields);
    }

    /**
     * A mutable builder with a fluent API for creating an ImmutableFilteredTopic.
     */
    @NotThreadSafe
    static final class ImmutableFilteredTopicBuilder implements FilteredTopicBuilder {

        private final Topic topic;
        @Nullable private Collection<String> namespaces;
        @Nullable private List<String> filters;
        @Nullable private ThingFieldSelector extraFields;

        private ImmutableFilteredTopicBuilder(final Topic topic) {
            this.topic = checkNotNull(topic, "topic");
            namespaces = null;
            filters = null;
            extraFields = null;
        }

        @Override
        public ImmutableFilteredTopicBuilder withNamespaces(@Nullable final Collection<String> namespaces) {
            if (supportsNamespaces()) {
                this.namespaces = namespaces;
            }
            return this;
        }

        @Override
        public ImmutableFilteredTopicBuilder withFilter(@Nullable final CharSequence filter) {
            return withFilters(null != filter ? Collections.singletonList(filter) : null);
        }

        @Override
        public ImmutableFilteredTopicBuilder withFilters(@Nullable final Collection<? extends CharSequence> filters) {
            if (supportsFilters()) {
                this.filters = null != filters
                        ? filters.stream().map(CharSequence::toString).collect(Collectors.toList())
                        : null;
            }
            return this;
        }

        @Override
        public ImmutableFilteredTopicBuilder withExtraFields(@Nullable final ThingFieldSelector extraFields) {
            if (supportsExtraFields()) {
                this.extraFields = extraFields;
            }
            return this;
        }

        @Override
        public ImmutableFilteredTopic build() {
            return new ImmutableFilteredTopic(this);
        }

        private boolean supportsNamespaces() {
            return Topic.CONNECTION_ANNOUNCEMENTS != topic;
        }

        private boolean supportsFilters() {
            return Topic.POLICY_ANNOUNCEMENTS != topic && Topic.CONNECTION_ANNOUNCEMENTS != topic;
        }

        private boolean supportsExtraFields() {
            return Topic.POLICY_ANNOUNCEMENTS != topic && Topic.CONNECTION_ANNOUNCEMENTS != topic;
        }

    }

    @NotThreadSafe
    private static final class FilteredTopicStringParser {

        private final String filteredTopicString;
        private final List<String> filterValues;

        private FilteredTopicStringParser(final String filteredTopicString) {
            this.filteredTopicString = filteredTopicString;
            filterValues = new ArrayList<>(1);
        }

        ImmutableFilteredTopic parse() {
            String topicName = filteredTopicString;
            @Nullable String queryParamsString = null;
            if (filteredTopicString.contains(QUERY_DELIMITER)) {
                final String[] splitString = filteredTopicString.split("\\" + QUERY_DELIMITER, 2);
                topicName = splitString[0];
                queryParamsString = splitString[1];
            }
            final Map<String, String> queryParameters = parseQueryParameters(queryParamsString);

            return getBuilder(parseTopic(topicName))
                    .withNamespaces(parseNamespaces(queryParameters.get(NAMESPACES_ARG)))
                    .withFilters(filterValues.isEmpty() ? null : filterValues)
                    .withExtraFields(parseExtraFields(queryParameters.get(EXTRA_FIELDS_ARG)))
                    .build();
        }

        private Topic parseTopic(final String topicName) {
            return Topic.forName(topicName)
                    .orElseThrow(() -> TopicParseException.newBuilder(filteredTopicString,
                            "Unknown topic: " + topicName).build());
        }

        private Map<String, String> parseQueryParameters(@Nullable final String queryParamsString) {
            if (null == queryParamsString || queryParamsString.isEmpty()) {
                return Collections.emptyMap();
            }
            final Map<String, String> queryParameters = new HashMap<>(4);
            for (final String paramString : queryParamsString.split(QUERY_ARG_DELIMITER)) {
                final String[] queryParamPair = paramString.split(QUERY_ARG_VALUE_DELIMITER, 2);
                if (2 != queryParamPair.length) {
                    continue;
                }
                final String name = urlDecode(queryParamPair[0]);
                final String value = urlDecode(queryParamPair[1]);
                if (FILTER_ARG.equals(name)) {
                    // the filter parameter is the only repeatable one: values are collected in insertion order
                    filterValues.add(value);
                } else if (null != queryParameters.putIfAbsent(name, value)) {
                    throw new IllegalStateException("Duplicate key " + name);
                }
            }
            return queryParameters;
        }

        private static String urlDecode(final String value) {
            try {
                return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
            } catch (final UnsupportedEncodingException e) {
                return URLDecoder.decode(value);
            }
        }

        private static Collection<String> parseNamespaces(@Nullable final String namespacesString) {
            if (null != namespacesString && !namespacesString.isEmpty()) {
                return Arrays.asList(namespacesString.split(","));
            }
            return Collections.emptyList();
        }

        @Nullable
        private static ThingFieldSelector parseExtraFields(@Nullable final String extraFieldsString) {
            if (null != extraFieldsString && !extraFieldsString.isEmpty()) {
                return ThingFieldSelector.fromString(extraFieldsString);
            }
            return null;
        }

    }

}
