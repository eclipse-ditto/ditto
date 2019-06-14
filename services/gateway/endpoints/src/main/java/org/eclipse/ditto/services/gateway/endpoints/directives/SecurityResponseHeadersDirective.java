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
package org.eclipse.ditto.services.gateway.endpoints.directives;

import static akka.http.javadsl.server.Directives.respondWithHeaders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.headers.RawHeader;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;

/**
 * Custom Akka Http directive which adds security related HTTP headers to the response.
 */
@Immutable
public final class SecurityResponseHeadersDirective {

    private static final String X_FRAME_OPTIONS = "X-Frame-Options";
    private static final String SAMEORIGIN = "SAMEORIGIN";

    private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String NOSNIFF = "nosniff";

    private static final String X_XSS_PROTECTION = "X-XSS-Protection";
    private static final String MODE_BLOCK = "1; mode=block";

    private SecurityResponseHeadersDirective() {
        throw new AssertionError();
    }

    /**
     * Adds security related HTTP headers to the response.
     *
     * @param inner the inner Route to wrap with the response headers
     * @return the new Route wrapping {@code inner} with the response headers
     */
    public static Route addSecurityResponseHeaders(final Supplier<Route> inner) {
        return Directives.extractActorSystem(actorSystem -> respondWithHeaders(createSecurityResponseHeaders(), inner));
    }

    static Iterable<HttpHeader> createSecurityResponseHeaders() {
        final List<HttpHeader> headers = new ArrayList<>(3);
        headers.add(RawHeader.create(X_FRAME_OPTIONS, SAMEORIGIN));
        headers.add(RawHeader.create(X_CONTENT_TYPE_OPTIONS, NOSNIFF));
        headers.add(RawHeader.create(X_XSS_PROTECTION, MODE_BLOCK));

        return Collections.unmodifiableList(headers);
    }

}
