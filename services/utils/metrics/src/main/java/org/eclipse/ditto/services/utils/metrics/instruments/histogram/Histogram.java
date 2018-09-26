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
package org.eclipse.ditto.services.utils.metrics.instruments.histogram;

import org.eclipse.ditto.services.utils.metrics.instruments.ResettableMetricInstrument;
import org.eclipse.ditto.services.utils.metrics.instruments.TaggedMetricInstrument;

/**
 * A histogram metric measures the statistical distribution of values in a stream of data.
 */
public interface Histogram extends ResettableMetricInstrument, TaggedMetricInstrument<Histogram> {

    /**
     * Records the specified value in the histogram.
     *
     * @param value The value to record.
     * @return This histogram.
     */
    Histogram record(Long value);

    /**
     * Gets all recorded values.
     *
     * @return An array of all recorded values.
     */
    Long[] getRecordedValues();
}
