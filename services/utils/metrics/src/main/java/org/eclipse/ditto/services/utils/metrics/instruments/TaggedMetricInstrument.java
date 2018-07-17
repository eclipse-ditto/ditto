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
package org.eclipse.ditto.services.utils.metrics.instruments;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * {@link MetricInstrument} which is able to be tagged with keys and values.
 *
 * @param <T> the type of the MetricInstrument itself
 */
public interface TaggedMetricInstrument<T extends MetricInstrument> extends MetricInstrument {

    /**
     * Adds the given tag to the timer.
     * Already existing tags with the same key will be overridden.
     *
     * @param key They key of the tag.
     * @param value The value of the tag.
     * @return The TracingTimerBuilder.
     */
    default T tag(final String key, final long value) {
        return tag(key, Long.toString(value));
    }

    /**
     * Adds the given tag to the timer.
     * Already existing tags with the same key will be overridden.
     *
     * @param key They key of the tag.
     * @param value The value of the tag.
     * @return The TracingTimerBuilder.
     */
    default T tag(final String key, final double value) {
        return tag(key, Double.toString(value));
    }

    /**
     * Adds the given tag to the timer.
     * Already existing tags with the same key will be overridden.
     *
     * @param key They key of the tag.
     * @param value The value of the tag.
     * @return The TracingTimerBuilder.
     */
    default T tag(final String key, final boolean value) {
        return tag(key, Boolean.toString(value));
    }

    /**
     * Adds the given tag to the timer.
     * Already existing tags with the same key will be overridden.
     *
     * @param key They key of the tag.
     * @param value The value of the tag.
     * @return The TracingTimerBuilder.
     */
    T tag(final String key, final String value);

    /**
     * Adds tags to the timer.
     * Already existing tags with the same key will be overridden.
     *
     * @param tags Additional tags for this tracing.
     * @return The TracingTimerBuilder.
     */
    T tags(final Map<String, String> tags);

    /**
     * Gets the value of the tag with the given key.
     *
     * @param key The key of the tag.
     * @return The value of the tag with the given key.
     */
    @Nullable
    String getTag(String key);

    /**
     * Gets the map containing all tags.
     *
     * @return All tags.
     */
    Map<String, String> getTags();
}
