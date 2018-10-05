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
package org.eclipse.ditto.services.utils.metrics.instruments.gauge;

import org.eclipse.ditto.services.utils.metrics.instruments.ResettableMetricInstrument;
import org.eclipse.ditto.services.utils.metrics.instruments.TaggedMetricInstrument;

/**
 * A gauge metric is an instantaneous measurement of a long value. You can increment and decrement its value.
 */
public interface Gauge extends ResettableMetricInstrument, TaggedMetricInstrument<Gauge> {

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
     * Gets the current value of the gauge.
     *
     * @return The current value of the gauge.
     */
    Long get();
}
