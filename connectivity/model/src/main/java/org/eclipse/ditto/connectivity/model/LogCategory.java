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
 * Defines the known log categories for connection logs.
 */
public enum LogCategory {
    /**
     * Category for logs related to a source.
     */
    SOURCE("source"),
    /**
     * Category for logs related to a target.
     */
    TARGET("target"),
    /**
     * Category for logs related to a response.
     */
    RESPONSE("response"),
    /**
     * Category for logs related to a connection (and not related to a single source, target or response).
     */
    CONNECTION("connection");

    private final String name;

    LogCategory(final String theName) {
        this.name = theName;
    }

    /**
     * @return the label which can be used in a JSON representation.
     */
    public String getName() {
        return name;
    }

    /**
     * @param name name of the LogCategory.
     * @return the LogCategory matching the given name.
     */
    public static Optional<LogCategory> forName(final CharSequence name) {
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
