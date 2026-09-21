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
     * Sets the given filter to this builder.
     *
     * @param filter the optional RQL filter of the topic to be built.
     * @return this builder instance to allow method chaining.
     */
    FilteredTopicBuilder withFilter(@Nullable CharSequence filter);

    /**
     * Sets the given placeholder pipeline expressions (one repeatable {@code fn-filter} query parameter each) to
     * this builder. All of them must match for the topic to be published (AND semantics). Ignored for topics which
     * do not support filters (policy and connection announcements).
     *
     * @param fnFilters the placeholder pipeline expressions of the topic to be built, e.g.
     * {@code header:ditto-originator|fn:filter('ne','some:subject')}; {@code null} or empty for none.
     * @return this builder instance to allow method chaining.
     * @since 4.0.0
     */
    FilteredTopicBuilder withFnFilters(@Nullable Collection<? extends CharSequence> fnFilters);

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
