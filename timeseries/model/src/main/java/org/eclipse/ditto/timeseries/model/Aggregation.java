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
package org.eclipse.ditto.timeseries.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * Aggregation functions supported by the Timeseries API for downsampling and bucketed summarisation
 * of timeseries data points.
 * <p>
 * Wire format uses the lowercase token (e.g. {@code avg}, {@code first}); see {@link #getName()}.
 *
 * @since 4.0.0
 */
@Immutable
public enum Aggregation implements CharSequence {

    /**
     * Arithmetic mean of values in each time bucket.
     */
    AVG("avg"),

    /**
     * Minimum value in each time bucket.
     */
    MIN("min"),

    /**
     * Maximum value in each time bucket.
     */
    MAX("max"),

    /**
     * Sum of values in each time bucket.
     */
    SUM("sum"),

    /**
     * Number of data points in each time bucket.
     */
    COUNT("count"),

    /**
     * First value in each time bucket (chronologically).
     */
    FIRST("first"),

    /**
     * Last value in each time bucket (chronologically).
     */
    LAST("last"),

    /**
     * Rate of change between consecutive values (per second). Useful for converting cumulative
     * measurements to rates.
     */
    DERIVATIVE("derivative"),

    /**
     * Non-negative derivative — handles counter resets by treating a decreasing value as the start
     * of a fresh count rather than producing a negative rate.
     */
    RATE("rate"),

    /**
     * Area under the curve (trapezoidal integration) in value-seconds. Useful for converting rate
     * measurements to cumulative totals (e.g. power → energy).
     */
    INTEGRAL("integral"),

    /**
     * Sample standard deviation of values in each time bucket.
     */
    STDDEV("stddev"),

    /**
     * Nth percentile of values in each time bucket. The percentile (0–100) is supplied separately
     * via the query parameter {@code percentile}.
     */
    PERCENTILE("percentile");

    /** The per-bucket aggregations — those expressible as a single {@code $group} accumulator. */
    private static final EnumSet<Aggregation> BUCKETED =
            EnumSet.of(AVG, MIN, MAX, SUM, COUNT, FIRST, LAST, STDDEV);

    private final String name;

    Aggregation(final String name) {
        this.name = name;
    }

    /**
     * Returns the wire-format name of this aggregation.
     *
     * @return the name as used on the wire (lowercase token).
     */
    public String getName() {
        return name;
    }

    /**
     * Whether this aggregation is computed per {@code step} bucket via a single {@code $group}
     * accumulator and therefore requires a {@code step} to be set. The window-style functions
     * ({@link #DERIVATIVE}, {@link #RATE}, {@link #INTEGRAL}, {@link #PERCENTILE}) return
     * {@code false} — they operate on raw points (and {@code step} is optional for them).
     *
     * @return {@code true} for the per-bucket aggregations (avg, min, max, sum, count, first, last,
     * stddev); {@code false} for the window-style functions.
     */
    public boolean requiresStep() {
        return BUCKETED.contains(this);
    }

    /**
     * Returns the {@code Aggregation} for the given wire-format {@code name} if it exists.
     *
     * @param name the wire-format name.
     * @return the matching {@code Aggregation} or an empty {@code Optional} if no match.
     * @throws NullPointerException if {@code name} is {@code null}.
     */
    public static Optional<Aggregation> forName(final CharSequence name) {
        checkNotNull(name, "name");
        return Arrays.stream(values())
                .filter(a -> a.name.contentEquals(name))
                .findFirst();
    }

    @Override
    public int length() {
        return name.length();
    }

    @Override
    public char charAt(final int index) {
        return name.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return name.subSequence(start, end);
    }

    @Override
    public String toString() {
        return name;
    }
}
