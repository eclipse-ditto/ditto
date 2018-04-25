/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.model.connectivity;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Optional;

/**
 * An enumeration of connection types of a {@link Connection}.
 */
public enum ConnectionType implements CharSequence {

    /**
     * Indicates an AMQP 0.9.1 connection.
     */
    AMQP_091("amqp-091"),

    /**
     * Indicates an AMQP 1.0 connection.
     */
    AMQP_10("amqp-10");

    private final String name;

    ConnectionType(final String name) {

        this.name = name;
    }

    /**
     * Returns the name of this {@code ConnectionStatus}.
     *
     * @return the name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the {@code ConnectionType} for the given {@code name} if it exists.
     *
     * @param name the name.
     * @return the ConnectionType or an empty optional.
     */
    public static Optional<ConnectionType> forName(final CharSequence name) {
        checkNotNull(name, "Name");
        return Arrays.stream(values())
                .filter(c -> c.name.contentEquals(name))
                .findFirst();
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
