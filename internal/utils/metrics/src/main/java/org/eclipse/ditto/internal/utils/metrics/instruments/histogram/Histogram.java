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
package org.eclipse.ditto.internal.utils.metrics.instruments.histogram;

import org.eclipse.ditto.internal.utils.metrics.instruments.ResettableMetricInstrument;
import org.eclipse.ditto.internal.utils.metrics.instruments.TaggedMetricInstrument;

/**
 * A histogram metric measures the statistical distribution of values in a stream of data.
 */
public interface Histogram extends ResettableMetricInstrument, TaggedMetricInstrument<Histogram> {

    @Override
    default Histogram self() {
        return this;
    }

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
