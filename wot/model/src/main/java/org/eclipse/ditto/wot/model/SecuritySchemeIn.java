/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Optional;

/**
 * SecuritySchemeScheme enlists all available {@link SecurityScheme} {@code "in"} values.
 *
 * @since 2.4.0
 */
public enum SecuritySchemeIn implements CharSequence {
    HEADER("header"),
    QUERY("query"),
    BODY("body"),
    COOKIE("cookie"),
    URI("uri"),
    AUTO("auto");

    private final String name;

    SecuritySchemeIn(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * Returns the {@code SecuritySchemeIn} for the given {@code name} if it exists.
     *
     * @param name the name.
     * @return the SecuritySchemeIn or an empty optional.
     */
    public static Optional<SecuritySchemeIn> forName(final CharSequence name) {
        checkNotNull(name, "name");
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
