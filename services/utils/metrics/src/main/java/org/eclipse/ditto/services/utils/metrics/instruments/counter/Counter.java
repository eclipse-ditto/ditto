/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.metrics.instruments.counter;

import org.eclipse.ditto.services.utils.metrics.instruments.ResettableMetricInstrument;
import org.eclipse.ditto.services.utils.metrics.instruments.TaggedMetricInstrument;

/**
 * A counter metric is a gauge which can only be incremented.
 */
public interface Counter extends ResettableMetricInstrument, TaggedMetricInstrument<Counter> {

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
