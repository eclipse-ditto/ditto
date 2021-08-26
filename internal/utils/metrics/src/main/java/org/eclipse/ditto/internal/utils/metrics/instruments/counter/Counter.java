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
package org.eclipse.ditto.internal.utils.metrics.instruments.counter;

import org.eclipse.ditto.internal.utils.metrics.instruments.ResettableMetricInstrument;
import org.eclipse.ditto.internal.utils.metrics.instruments.TaggedMetricInstrument;

/**
 * A counter metric is a gauge which can only be incremented.
 */
public interface Counter extends ResettableMetricInstrument, TaggedMetricInstrument<Counter> {

    @Override
    default Counter self() {
        return this;
    }

    /**
     * Increments the value of the counter by one.
     *
     * @return This counter.
     */
    Counter increment();


    /**
     * Increments the value of the counter by one for the specified values of times.
     *
     * @param times how many times to increment this counter.
     * @return This counter.
     */
    Counter increment(long times);

    /**
     * Gets the current count of the counter.
     *
     * @return the current count of this counter.
     */
    long getCount();
}
