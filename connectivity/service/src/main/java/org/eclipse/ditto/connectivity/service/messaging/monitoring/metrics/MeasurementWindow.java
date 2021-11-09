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
package org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Defined measurement windows.
 */
public enum MeasurementWindow {

    /**
     * Window of 1 minute with a resolution of 10 seconds.
     */
    ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION(Duration.ofMinutes(1), Duration.ofSeconds(10)),

    /**
     * Window of 1 hour with a resolution of 1 minute.
     */
    ONE_HOUR_WITH_ONE_MINUTE_RESOLUTION(Duration.ofHours(1), Duration.ofMinutes(1)),

    /**
     * Window of 24 hours / 1 day with a resolution of 1 hour.
     */
    ONE_DAY_WITH_ONE_HOUR_RESOLUTION(Duration.ofHours(24), Duration.ofHours(1)),

    /**
     * Window of 1 minute with a resolution of 1 minute.
     */
    ONE_MINUTE_WITH_ONE_MINUTE_RESOLUTION(Duration.ofMinutes(1), Duration.ofMinutes(1)),

    /**
     * Window of 24 hours / 1 day with a resolution of 1 minute.
     */
    ONE_DAY_WITH_ONE_MINUTE_RESOLUTION(Duration.ofHours(24), Duration.ofMinutes(1));

    private final Duration window;
    private final Duration resolution;
    private final String label;

    MeasurementWindow(final Duration window, final Duration resolution) {
        this.label = window.toString();
        this.window = window;
        this.resolution = resolution;
    }

    /**
     * @param name the name for which to find a {@link MeasurementWindow}.
     * @return the {@link MeasurementWindow} or an empty Optional
     */
    public static Optional<MeasurementWindow> forName(final String name) {
        return Arrays.stream(values()).filter(v->v.label.equals(name)).findFirst();
    }

    /**
     * @return the ISO-8601 string representation of the interval
     */
    public String getLabel() {
        return window.toString() + "(" + resolution.toString() + ")";
    }

    public Duration getWindow() {
        return window;
    }

    public Duration getResolution() {
        return resolution;
    }
}
