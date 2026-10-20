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
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * Strategies for filling gaps in downsampled timeseries data when a time bucket contains no data
 * points.
 * <p>
 * Wire format uses the lowercase token (e.g. {@code null}, {@code previous}); see {@link #getName()}.
 *
 * @since 4.0.0
 */
@Immutable
public enum FillStrategy implements CharSequence {

    /**
     * Leaves empty buckets as JSON {@code null} values. Default behaviour when no fill strategy
     * is requested.
     */
    NULL("null"),

    /**
     * Carries the most recent prior value forward into empty buckets.
     */
    PREVIOUS("previous"),

    /**
     * Linearly interpolates between the surrounding non-empty buckets.
     */
    LINEAR("linear"),

    /**
     * Fills empty buckets with the numeric value zero.
     */
    ZERO("zero");

    private final String name;

    FillStrategy(final String name) {
        this.name = name;
    }

    /**
     * Returns the wire-format name of this fill strategy.
     *
     * @return the name as used on the wire (lowercase token).
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the {@code FillStrategy} for the given wire-format {@code name} if it exists.
     *
     * @param name the wire-format name.
     * @return the matching {@code FillStrategy} or an empty {@code Optional} if no match.
     * @throws NullPointerException if {@code name} is {@code null}.
     */
    public static Optional<FillStrategy> forName(final CharSequence name) {
        checkNotNull(name, "name");
        return Arrays.stream(values())
                .filter(s -> s.name.contentEquals(name))
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
