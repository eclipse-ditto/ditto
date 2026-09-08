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
 * The chronological order in which a raw timeseries read returns its data points.
 * <p>
 * {@link #ASC} (oldest first) is the default and matches the natural bucket order of downsampled
 * reads. {@link #DESC} (newest first) suits "scroll back into the past" UIs paging with a
 * {@link TimeseriesCursor}; it is only supported for raw reads. Wire format uses the lowercase token
 * (see {@link #getName()}).
 *
 * @since 4.0.0
 */
@Immutable
public enum SortOrder {

    /**
     * Ascending by timestamp — oldest data point first. The default.
     */
    ASC("asc"),

    /**
     * Descending by timestamp — newest data point first.
     */
    DESC("desc");

    private final String name;

    SortOrder(final String name) {
        this.name = name;
    }

    /**
     * Returns the wire-format name of this sort order.
     *
     * @return the name as used on the wire (lowercase token).
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the {@code SortOrder} for the given wire-format {@code name} if it exists.
     *
     * @param name the wire-format name.
     * @return the matching {@code SortOrder} or an empty {@code Optional} if no match.
     * @throws NullPointerException if {@code name} is {@code null}.
     */
    public static Optional<SortOrder> forName(final CharSequence name) {
        checkNotNull(name, "name");
        return Arrays.stream(values())
                .filter(order -> order.name.contentEquals(name))
                .findFirst();
    }

    @Override
    public String toString() {
        return name;
    }
}
