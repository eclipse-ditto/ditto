/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.timeseries.mongodb;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * Granularity of a MongoDB Time Series collection. Selects the bucketing strategy MongoDB applies
 * internally. Match this to the dominant ingestion cadence:
 * <ul>
 *   <li>{@link #SECONDS} for sub-minute sampling (high-frequency sensors, high-cadence telemetry).</li>
 *   <li>{@link #MINUTES} for samples on the order of minutes.</li>
 *   <li>{@link #HOURS} for samples on the order of hours and slower (attribute timeseries,
 *       maintenance dates).</li>
 * </ul>
 * <p>
 * Granularity is configured at collection creation and cannot be changed afterwards.
 */
@Immutable
public enum Granularity {

    SECONDS("seconds"),
    MINUTES("minutes"),
    HOURS("hours");

    private final String name;

    Granularity(final String name) {
        this.name = name;
    }

    /**
     * @return the MongoDB-compatible token (lowercase) for this granularity.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the {@code Granularity} for the given MongoDB token.
     *
     * @param name the lowercase token (e.g. {@code "seconds"}).
     * @return the matching value or empty if unknown.
     * @throws NullPointerException if {@code name} is {@code null}.
     */
    public static Optional<Granularity> forName(final CharSequence name) {
        checkNotNull(name, "name");
        return Arrays.stream(values()).filter(g -> g.name.contentEquals(name)).findFirst();
    }

    @Override
    public String toString() {
        return name;
    }
}
