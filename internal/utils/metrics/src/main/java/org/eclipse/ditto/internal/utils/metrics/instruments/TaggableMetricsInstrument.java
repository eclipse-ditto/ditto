/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.metrics.instruments;

import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;

/**
 * {@link MetricInstrument} which is able to be tagged with keys and values.
 *
 * @param <T> the type of the MetricInstrument itself
 */
public interface TaggableMetricsInstrument<T extends MetricInstrument> extends MetricInstrument {

    /**
     * Adds the given tag to the timer.
     * Already existing tags with the same key will be overridden.
     *
     * @param key he key of the tag.
     * @param value the value of the tag.
     * @return this instance to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code key} is blank.
     * @see Tag#of(String, boolean)
     */
    default T tag(final String key, final boolean value) {
        return tag(Tag.of(key, value));
    }

    /**
     * Adds the given tag to the timer.
     * Already existing tags with the same key will be overridden.
     *
     * @param key they key of the tag.
     * @param value the value of the tag.
     * @return this instance to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code key} or {@code value} is blank.
     * @see Tag#of(String, String)
     */
    default T tag(final String key, final String value) {
        return tag(Tag.of(key, value));
    }

    /**
     * Adds the specified {@code Tag} argument.
     * An already existing tag with the same key will be overridden.
     *
     * @param tag the tag to be added.
     * If a tag with the same key as {@code tag} was already present then its value will be overwritten.
     * @return this instance to allow method chaining.
     * @throws NullPointerException if {@code tag} is {@code null}.
     */
    T tag(Tag tag);

    /**
     * Adds tags to the timer.
     * Already existing tags with the same key will be overridden.
     *
     * @param tags additional tags for this tracing.
     * @return this instance to allow method chaining.
     * @throws NullPointerException if {@code tags} is {@code null}.
     */
    T tags(TagSet tags);

    /**
     * @return this instance.
     */
    T self();

}
