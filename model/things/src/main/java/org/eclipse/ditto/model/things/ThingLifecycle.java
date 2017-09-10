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
package org.eclipse.ditto.model.things;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * An enumeration of a Thing's lifecycle.
 */
public enum ThingLifecycle {

    /**
     * Signals that a Thing is active.
     */
    ACTIVE,

    /**
     * Signals that a Thing is deleted.
     */
    DELETED;

    /**
     * Returns the {@code ThingLifecycle} with the given name.
     *
     * @param name the name of the lifecycle to get.
     * @return the lifecycle with the given name or an empty optional.
     */
    public static Optional<ThingLifecycle> forName(final String name) {
        return Stream.of(values()) //
                .filter(l -> Objects.equals(l.name(), name)) //
                .findAny();
    }

}
