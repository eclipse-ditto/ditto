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
package org.eclipse.ditto.policies.model;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nullable;

/**
 * An enumeration of a Policy's lifecycle.
 */
public enum PolicyLifecycle {

    /**
     * Signals that a Policy is active.
     */
    ACTIVE,

    /**
     * Signals that a Policy is deleted.
     */
    DELETED;

    /**
     * Returns the {@code PolicyLifecycle} with the given name.
     *
     * @param name the name of the lifecycle to get.
     * @return the lifecycle with the given name or an empty optional.
     */
    public static Optional<PolicyLifecycle> forName(@Nullable final CharSequence name) {
        return Stream.of(values())
                .filter(l -> Objects.equals(l.name(), String.valueOf(name)))
                .findAny();
    }

}
