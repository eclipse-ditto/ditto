/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.common;

import java.util.Arrays;
import java.util.Optional;

/**
 * An enumeration of different response types for a request.
 *
 * @since 1.2.0
 */
public enum ResponseType implements CharSequence {

    /**
     * Type of error responses.
     */
    ERROR("error"),

    /**
     * Type of negative acknowledgements responses.
     */
    NACK("nack"),

    /**
     * Type of normal responses. This includes positive acknowledgements.
     */
    RESPONSE("response");

    private final String name;

    ResponseType(final String name) {
        this.name = name;
    }

    /**
     * Returns the {@code ResponseType} for the given {@code name} if it exists.
     *
     * @param name the name.
     * @return the ResponseType or an empty optional.
     */
    public static Optional<ResponseType> fromName(final String name) {
        final String lowerCaseName = name.toLowerCase();
        return Arrays.stream(values())
                .filter(responseType -> responseType.name.equals(lowerCaseName))
                .findAny();
    }

    /**
     * Returns the name of this {@code ResponseType}.
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
