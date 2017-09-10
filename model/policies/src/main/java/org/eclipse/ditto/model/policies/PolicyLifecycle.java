/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.policies;

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
