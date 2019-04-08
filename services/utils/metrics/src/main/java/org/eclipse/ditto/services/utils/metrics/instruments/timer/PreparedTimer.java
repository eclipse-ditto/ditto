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
package org.eclipse.ditto.services.utils.metrics.instruments.timer;

import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.services.utils.metrics.instruments.ResettableMetricInstrument;
import org.eclipse.ditto.services.utils.metrics.instruments.TaggedMetricInstrument;

/**
 * A Timer metric which is prepared to be {@link #start() started}.
 */
public interface PreparedTimer extends Timer, ResettableMetricInstrument, TaggedMetricInstrument<PreparedTimer> {

    /**
     * Starts the Timer. This method is package private so only {@link DefaultTimerBuilder} can start
     * this timer.
     *
     * @return The started {@link StartedTimer timer}.
     */
    StartedTimer start();

    /**
     * Records the given time.
     *
     * @param time The time to record.
     * @param timeUnit The unit of the time to record.
     * @return This timer.
     */
    PreparedTimer record(long time, TimeUnit timeUnit);

    /**
     * Gets recorded times in nanoseconds.
     *
     * @return recorded times in nanoseconds.
     */
    Long[] getRecords();

    /**
     * Get number of records.
     *
     * @return The number of records for this timer.
     */
    Long getNumberOfRecords();
}
