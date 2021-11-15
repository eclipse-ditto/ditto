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
package org.eclipse.ditto.internal.utils.metrics.instruments.gauge;

import org.eclipse.ditto.internal.utils.metrics.instruments.ResettableMetricInstrument;
import org.eclipse.ditto.internal.utils.metrics.instruments.TaggedMetricInstrument;

/**
 * A gauge metric is an instantaneous measurement of a long value. You can increment and decrement its value.
 */
public interface Gauge extends ResettableMetricInstrument, TaggedMetricInstrument<Gauge> {

    @Override
    default Gauge self() {
        return this;
    }

    /**
     * Increments the value of the gauge by one.
     *
     * @return This gauge.
     */
    Gauge increment();

    /**
     * Decrements the value of the gauge by one.
     *
     * @return This gauge.
     */
    Gauge decrement();

    /**
     * Sets the value of the gauge to the given value.
     *
     * @param value The value the gauge should be set to.
     */
    void set(Long value);

    /**
     * Sets the value of the gauge to the given value.
     *
     * @param value The value the gauge should be set to.
     */
    void set(Double value);

    /**
     * Gets the current value of the gauge.
     *
     * @return The current value of the gauge.
     */
    Long get();
}
