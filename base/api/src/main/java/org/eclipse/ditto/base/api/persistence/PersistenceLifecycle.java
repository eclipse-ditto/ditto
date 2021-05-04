/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.api.persistence;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * An enumeration of an entity's lifecycle states.
 */
public enum PersistenceLifecycle {

    /**
     * Signals that an entity is active.
     */
    ACTIVE,

    /**
     * Signals that an entity is deleted.
     */
    DELETED;

    /**
     * Returns the {@code PersistenceLifecycle} with the given name.
     *
     * @param name the name of the lifecycle to get.
     * @return the lifecycle with the given name or an empty optional.
     */
    public static Optional<PersistenceLifecycle> forName(final String name) {
        return Stream.of(values())
                .filter(l -> Objects.equals(l.name(), name))
                .findAny();
    }

}
