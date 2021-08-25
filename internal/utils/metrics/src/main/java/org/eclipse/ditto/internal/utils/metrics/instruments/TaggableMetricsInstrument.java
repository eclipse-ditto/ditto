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

import java.util.Map;

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
    T tag(String key, String value);

    /**
     * Adds tags to the timer.
     * Already existing tags with the same key will be overridden.
     *
     * @param tags Additional tags for this tracing.
     * @return The TracingTimerBuilder.
     */
    T tags(Map<String, String> tags);

    /**
     * @return this instance.
     */
    T self();
}
