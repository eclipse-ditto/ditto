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
package org.eclipse.ditto.model.connectivity;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

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
