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
package org.eclipse.ditto.model.connectivity;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Optional;

/**
 * An enumeration of status of connectivity resource.
 * TODO make Jsonifiable as it is sent in cluster
 */
public enum ConnectivityStatus implements CharSequence {

    /**
     * Indicates an open {@code Connection}.
     */
    OPEN("open"),

    /**
     * Indicates a closed {@code Connection}.
     */
    CLOSED("closed"),

    /**
     * Indicates a failed {@code Connection}.
     */
    FAILED("failed"),

    /**
     * Indicates an unknown status.
     */
    UNKNOWN("unknown");

    private final String name;

    ConnectivityStatus(final String name) {
        this.name = checkNotNull(name);
    }

    /**
     * Returns the {@code ConnectivityStatus} for the given {@code name} if it exists.
     *
     * @param name the name.
     * @return the ConnectivityStatus or an empty optional.
     */
    public static Optional<ConnectivityStatus> forName(final CharSequence name) {
        checkNotNull(name, "Name");
        return Arrays.stream(values())
                .filter(c -> c.name.contentEquals(name))
                .findFirst();
    }

    /**
     * Returns the name of this {@code ConnectivityStatus}.
     *
     * @return the name.
     */
    public String getName() {
        return name;
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
