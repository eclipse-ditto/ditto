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
package org.eclipse.ditto.gateway.service.security;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An enumeration of HTTP headers.
 */
public enum HttpHeader {

    /**
     * Authorization HTTP header.
     */
    AUTHORIZATION("authorization"),

    /**
     * x-correlation-id HTTP header.
     */
    X_CORRELATION_ID("x-correlation-id"),

    /**
     * HTTP header for authentication already done by e.g. a reverse proxy in front of Ditto (e.g. a nginx).
     */
    X_DITTO_PRE_AUTH("x-ditto-pre-authenticated");

    private static final Map<String, HttpHeader> BY_NAME =
            Arrays.stream(values()).collect(Collectors.toMap(HttpHeader::getName, Function.identity()));

    private final String name;

    HttpHeader(final String name) {
        this.name = name;
    }

    /**
     * Returns a {@code HttpHeader} from a given string representation.
     *
     * @param name the string representation.
     * @return the HttpHeader.
     */
    public static Optional<HttpHeader> fromName(final String name) {
        return Optional.ofNullable(BY_NAME.get(name));
    }

    /**
     * Returns the name of this {@link HttpHeader}.
     *
     * @return the name.
     */
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
