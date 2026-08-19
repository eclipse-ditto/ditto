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
package org.eclipse.ditto.connectivity.model;

import java.util.Collection;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.things.model.ThingFieldSelector;

/**
 * A mutable builder with a fluent API for creating a {@link FilteredTopic}.
 */
@NotThreadSafe
public interface FilteredTopicBuilder {

    /**
     * Sets the given namespaces to this builder.
     *
     * @param namespaces the namespaces for which the filter should be applied &ndash; if empty, all namespaces are
     * considered.
     * @return this builder instance to allow method chaining.
     */
    FilteredTopicBuilder withNamespaces(@Nullable Collection<String> namespaces);

    /**
     * Sets the given filter to this builder, replacing all previously set filters.
     *
     * @param filter the optional filter of the topic to be built.
     * @return this builder instance to allow method chaining.
     * @deprecated as of 3.10.0 a FilteredTopic may carry multiple filters; use {@link #withFilters(Collection)}
     * instead.
     */
    @Deprecated
    FilteredTopicBuilder withFilter(@Nullable CharSequence filter);

    /**
     * Sets the given filters to this builder, replacing all previously set filters. The insertion order is
     * preserved and determines the serialization order of the {@code filter} query parameters; two topics with
     * the same filters in different order are not equal.
     *
     * @param filters the filters of the topic to be built - each entry is either an RQL expression or a
     * placeholder pipeline expression starting with {@code fn:}.
     * @return this builder instance to allow method chaining.
     * @since 3.10.0
     */
    FilteredTopicBuilder withFilters(@Nullable Collection<? extends CharSequence> filters);

    /**
     * Sets the selector for the extra fields and their values to enrich outgoing signals of the topic to be built with.
     *
     * @param extraFields the extra fields.
     * @return this builder instance to allow method chaining.
     */
    FilteredTopicBuilder withExtraFields(@Nullable ThingFieldSelector extraFields);

    /**
     * Builds a filtered topic with the current properties of this builder instance.
     *
     * @return the filtered topic.
     */
    FilteredTopic build();

}
