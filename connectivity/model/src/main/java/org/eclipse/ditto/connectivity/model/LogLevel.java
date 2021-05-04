/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
 * Defines the log level for connection logs.
 */
public enum LogLevel {
    /**
     * Success logs.
     */
    SUCCESS("success"),
    /**
     * Failure logs.
     */
    FAILURE("failure");

    private final String level;

    LogLevel(final String theLevel) {
        this.level = theLevel;
    }

    /**
     * @return the label which can be used in a JSON representation.
     */
    public String getLevel() {
        return level;
    }

    /**
     * @param level level of the LogLevel.
     * @return the LogLevel matching the given level.
     */
    public static Optional<LogLevel> forLevel(final CharSequence level) {
        checkNotNull(level, "Level");
        return Arrays.stream(values())
                .filter(c -> c.level.contentEquals(level))
                .findFirst();
    }

    @Override
    public String toString() {
        return level;
    }

}
