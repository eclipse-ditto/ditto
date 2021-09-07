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
package org.eclipse.ditto.internal.utils.metrics.instruments.timer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.eclipse.ditto.internal.utils.metrics.instruments.ResettableMetricInstrument;
import org.eclipse.ditto.internal.utils.metrics.instruments.TaggedMetricInstrument;

/**
 * A Timer metric which is prepared to be {@link #start() started}.
 */
public interface PreparedTimer extends Timer, ResettableMetricInstrument, TaggedMetricInstrument<PreparedTimer> {

    @Override
    default PreparedTimer self() {
        return this;
    }

    /**
     * Starts the Timer. This method is package private so only {@link Timers} can start
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
     * Gets sum of all recorded times in nanoseconds.
     *
     * @return sum of all recorded times in nanoseconds.
     */
    Long getTotalTime();

    /**
     * Get number of records.
     *
     * @return The number of records for this timer.
     */
    Long getNumberOfRecords();

    /**
     * Specifies the maximum duration this timer should be running. It will expire after this time.
     *
     * @param maximumDuration The maximum duration.
     * @return A new prepared timer with the new maximum duration.
     */
    PreparedTimer maximumDuration(Duration maximumDuration);

    /**
     * Sets the handling of a timer after expiration. This will be executed in addition to a default handling which
     * stops the started timer.
     *
     * @param additionalExpirationHandling custom handling of timer expiration.
     * @return A new prepared timer with the new expiration handling.
     */
    PreparedTimer onExpiration(final Consumer<StartedTimer> additionalExpirationHandling);
}
