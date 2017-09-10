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
package org.eclipse.ditto.services.gateway.security.parser;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

/**
 * Container class for headers and cookies of a request.
 */
@Immutable
public final class RequestHeaders {

    private final Map<String, String> headers;
    private final Map<String, String> cookies;

    /**
     * Constructs a new {@code RequestHeaders} object.
     *
     * @param headers the headers of a HTTP request.
     * @param cookies the cookies of a HTTP request.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public RequestHeaders(final Map<String, String> headers, final Map<String, String> cookies) {
        requireNonNull(headers, "The headers must not be null!");
        requireNonNull(cookies, "The cookies must not be null!");

        this.headers = Collections.unmodifiableMap(headers);
        this.cookies = Collections.unmodifiableMap(cookies);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, String> getCookies() {
        return cookies;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "headers=" + headers + ", cookies=" + cookies + "]";
    }
}
