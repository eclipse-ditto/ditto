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
package org.eclipse.ditto.services.gateway.security;

import java.util.Arrays;
import java.util.Optional;

/**
 * An enumeration of HTTP headers.
 */
public enum HttpHeader {

    /**
     * Authorization HTTP header.
     */
    AUTHORIZATION("Authorization"),

    /**
     * WWW-Authenticate HTTP header.
     */
    WWW_AUTHENTICATE("WWW-Authenticate"),

    /**
     * Date HTTP header.
     */
    DATE("Date"),

    /**
     * Host HTTP header.
     */
    HOST("Host"),

    /**
     * Location HTTP header.
     */
    LOCATION("Location"),

    /**
     * Origin HTTP header.
     */
    ORIGIN("Origin"),


    /**
     * x-correlation-id HTTP header.
     */
    X_CORRELATION_ID("x-correlation-id"),

    /**
     * HTTP header for dummy authentication (for dev purposes).
     */
    X_DITTO_DUMMY_AUTH("x-ditto-dummy-auth");

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
        return Arrays.stream(values()).filter(header -> name.equals(header.toString())).findFirst();
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
