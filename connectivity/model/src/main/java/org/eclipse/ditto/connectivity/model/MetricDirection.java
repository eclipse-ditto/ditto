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
import java.util.Optional;

/**
 * Defines the direction of a counter e.g. if inbound or outbound messages are counted.
 */
public enum MetricDirection {

    /**
     * Inbound direction from external systems to Ditto.
     */
    INBOUND("inbound"),

    /**
     * Outbound direction from Ditto to external systems.
     */
    OUTBOUND("outbound");

    private final String name;

    MetricDirection(final String name) {
        this.name = name;
    }

    /**
     * @return the direction label
     */
    public String getName() {
        return name;
    }

    /**
     * @param name name of the MetricDirection
     * @return the MetricDirection matching the given name
     */
    public static Optional<MetricDirection> forName(final CharSequence name) {
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
