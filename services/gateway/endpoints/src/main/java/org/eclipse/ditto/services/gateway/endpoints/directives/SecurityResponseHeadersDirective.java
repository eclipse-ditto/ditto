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
package org.eclipse.ditto.services.gateway.endpoints.directives;

import static akka.http.javadsl.server.Directives.respondWithHeaders;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.ditto.services.gateway.starter.service.util.ConfigKeys;

import com.typesafe.config.Config;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.headers.RawHeader;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;

/**
 * Custom Akka Http directive which adds security related HTTP headers to the response.
 */
public final class SecurityResponseHeadersDirective {

    private static final String X_FRAME_OPTIONS = "X-Frame-Options";
    private static final String SAMEORIGIN = "SAMEORIGIN";

    private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String NOSNIFF = "nosniff";

    private static final String X_XSS_PROTECTION = "X-XSS-Protection";
    private static final String MODE_BLOCK = "1; mode=block";

    private static final String STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    private static final String MAX_AGE = "max-age=";
    private static final String INCLUDE_SUB_DOMAINS = " ; includeSubDomains";

    private SecurityResponseHeadersDirective() {
        // no op
    }

    /**
     * Adds security related HTTP headers to the response.
     *
     * @param inner the inner Route to wrap with the response headers
     * @return the new Route wrapping {@code inner} with the response headers
     */
    public static Route addSecurityResponseHeaders(final Supplier<Route> inner) {
        return Directives.extractActorSystem(actorSystem -> {
            final Config config = actorSystem.settings().config();
            final Iterable<HttpHeader> securityResponseHeaders = createSecurityResponseHeaders(config);
            return respondWithHeaders(securityResponseHeaders, inner);
        });
    }

    static Iterable<HttpHeader> createSecurityResponseHeaders(final Config config) {
        final boolean forceHttps = config.getBoolean(ConfigKeys.FORCE_HTTPS);
        return createSecurityResponseHeaders(forceHttps);
    }

    private static Iterable<HttpHeader> createSecurityResponseHeaders(final boolean forceHttps) {
        final List<HttpHeader> headers = new LinkedList<>();

        headers.add(RawHeader.create(X_FRAME_OPTIONS, SAMEORIGIN));
        headers.add(RawHeader.create(X_CONTENT_TYPE_OPTIONS, NOSNIFF));
        headers.add(RawHeader.create(X_XSS_PROTECTION, MODE_BLOCK));

        // only when FORCE_HTTPS = true:
        if (forceHttps) {
            final String xXssProtectionValue = MAX_AGE +
                    Duration.ofDays(365).getSeconds() // say the browser that we are a HSTS host for 1 year
                    + INCLUDE_SUB_DOMAINS;
            headers.add(RawHeader.create(STRICT_TRANSPORT_SECURITY, xXssProtectionValue));
        }

        return Collections.unmodifiableList(headers);
    }

}
