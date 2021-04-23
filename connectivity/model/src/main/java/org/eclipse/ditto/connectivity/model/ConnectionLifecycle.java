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

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nullable;

/**
 * An enumeration of a Connection's lifecycle.
 */
public enum ConnectionLifecycle {

    /**
     * Signals that a Connection is active.
     */
    ACTIVE,

    /**
     * Signals that a Connection is deleted.
     */
    DELETED;

    /**
     * Returns the {@code ConnectionLifecycle} with the given name.
     *
     * @param name the name of the lifecycle to get.
     * @return the lifecycle with the given name or an empty optional.
     */
    public static Optional<ConnectionLifecycle> forName(@Nullable final CharSequence name) {
        return Stream.of(values())
                .filter(l -> Objects.equals(l.name(), String.valueOf(name)))
                .findAny();
    }

}
