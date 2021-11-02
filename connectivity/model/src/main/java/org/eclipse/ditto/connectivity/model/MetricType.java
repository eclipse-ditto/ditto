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
package org.eclipse.ditto.connectivity.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Defines known metric types.
 */
public enum MetricType {

    /**
     * Counts inbound messages that were consumed.
     */
    CONSUMED("consumed", MetricDirection.INBOUND),

    /**
     * Counts outbound messages that were dispatched.
     */
    DISPATCHED("dispatched", MetricDirection.OUTBOUND),

    /**
     * Counts messages to external systems that passed the configured filter.
     */
    FILTERED("filtered", MetricDirection.OUTBOUND),

    /**
     * Counts mappings for messages.
     */
    MAPPED("mapped", MetricDirection.INBOUND, MetricDirection.OUTBOUND),

    /**
     * Counts messages that were dropped (not published by intention e.g. because no reply-to address was given).
     */
    DROPPED("dropped", MetricDirection.INBOUND, MetricDirection.OUTBOUND),

    /**
     * Counts enforcements for inbound messages.
     */
    ENFORCED("enforced", MetricDirection.INBOUND),

    /**
     * Counts messages published to external systems.
     */
    PUBLISHED("published", MetricDirection.OUTBOUND),

    /**
     * Counts inbound/outbound messages that were acknowledged.
     */
    ACKNOWLEDGED("acknowledged", MetricDirection.INBOUND, MetricDirection.OUTBOUND),

    /**
     * Signals if the connection was throttled i.e. 1 = throttled, 0 not throttled.
     */
    THROTTLED("throttled", MetricDirection.INBOUND);

    private final String name;
    private final List<MetricDirection> possibleMetricDirections;

    MetricType(final String name, final MetricDirection... possibleMetricDirections) {
        this.name = name;
        this.possibleMetricDirections = Arrays.asList(possibleMetricDirections);
    }

    /**
     * @return the label which can be used in a JSON representation.
     */
    public String getName() {
        return name;
    }

    /**
     * Checks whether the given {@code direction} is supported by this MetricType.
     *
     * @param direction the direction to check.
     * @return true if the {@code direction} is supported by this MetricType.
     */
    public boolean supportsDirection(final MetricDirection direction) {
        return possibleMetricDirections.contains(direction);
    }

    /**
     * @param name name of the MetricType
     * @return the MetricType matching the given name
     */
    public static Optional<MetricType> forName(final CharSequence name) {
        checkNotNull(name, "Name");
        return Arrays.stream(values())
                .filter(c -> c.name.contentEquals(name))
                .findFirst();
    }

    @Override
    public String toString() {
        return name;
    }
}
