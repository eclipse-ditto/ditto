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
package org.eclipse.ditto.base.api.devops;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Enumeration of LogLevels.
 */
public enum LogLevel {
    /**
     * Signals that logging should be disabled completely.
     */
    OFF("off"),

    /**
     * Signals that only errors should be logged.
     */
    ERROR("error"),

    /**
     * Signals that warnings and errors should be logged.
     */
    WARN("warn"),

    /**
     * Signals that info, warnings and errors should be logged.
     */
    INFO("info"),

    /**
     * Signals that debug, info, warnings and errors should be logged.
     */
    DEBUG("debug"),

    /**
     * Signals that trace, debug, info, warnings and errors should be logged.
     */
    TRACE("trace"),

    /**
     * Signals that all should be logged.
     */
    ALL("all");

    private final String level;

    LogLevel(final String level) {
        this.level = level;
    }

    /**
     * Returns the LogLevel of the specified {@code identifier}.
     *
     * @param identifier the identifier to look up in the LogLevels.
     * @return the LogLevel to the given {@code identifier}
     */
    public static Optional<LogLevel> forIdentifier(final String identifier) {
        return Stream.of(values()) //
                .filter(r -> Objects.equals(r.level, identifier.toLowerCase())) //
                .findFirst();
    }

    /**
     * Returns the LogLevel's identifier.
     *
     * @return the LogLevel's identifier.
     */
    public String getIdentifier() {
        return level;
    }
}
