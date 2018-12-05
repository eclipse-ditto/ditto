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
package org.eclipse.ditto.services.connectivity.messaging.metrics;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Defined measurement windows.
 */
public enum MeasurementWindow {

    ONE_MINUTE(Duration.ofMinutes(1), Duration.ofSeconds(10)),
    ONE_HOUR(Duration.ofHours(1), Duration.ofMinutes(1)),
    ONE_DAY(Duration.ofHours(24), Duration.ofHours(1));

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
        return window.toString() + "("+resolution.toString() + ")";
    }

    public Duration getWindow() {
        return window;
    }

    public Duration getResolution() {
        return resolution;
    }
}
