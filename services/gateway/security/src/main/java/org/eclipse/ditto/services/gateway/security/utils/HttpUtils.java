/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.gateway.security.utils;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.gateway.security.HttpHeader;
import org.eclipse.ditto.services.gateway.security.parser.RequestHeaders;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.headers.Authorization;
import akka.http.javadsl.model.headers.BasicHttpCredentials;
import akka.http.javadsl.model.headers.Cookie;
import akka.http.javadsl.server.RequestContext;

@Immutable
public final class HttpUtils {

    private static final String BASIC = "basic";

    private HttpUtils() {
        throw new AssertionError();
    }

    /**
     * Gets the request header with the given name.
     *
     * @param requestContext the RequestContext to extract the header from
     * @param name the name of the header
     * @return the header value or an empty {@link Optional}
     */
    public static Optional<String> getRequestHeader(final RequestContext requestContext, final String name) {
        return requestContext.getRequest()
                .getHeader(name)
                .map(akka.http.javadsl.model.HttpHeader::value);
    }

    /**
     * Extracts the {@link org.eclipse.ditto.services.gateway.security.parser.RequestHeaders} of the specified request.
     *
     * @param requestContext the RequestContext from which to extract the headers.
     * @return the extracted headers of {@code requestContext}.
     * @throws NullPointerException if {@code requestContext} is {@code null}.
     */
    public static RequestHeaders getHeaders(final RequestContext requestContext) {
        checkNotNull(requestContext, "requestContext");

        final Map<String, String> headers = new HashMap<>();
        requestContext.getRequest().getHeaders().forEach(
                header -> headers.put(header.name().toLowerCase(), header.value()));

        final Iterable<akka.http.javadsl.model.headers.HttpCookiePair> cookies = requestContext.getRequest()
                .getHeader(Cookie.class)
                .map(akka.http.javadsl.model.headers.Cookie::getCookies)
                .orElse(Collections.emptyList());
        final Map<String, String> cookieMap = new HashMap<>();
        cookies.forEach(cookie -> cookieMap.put(cookie.name(), cookie.value()));

        return new RequestHeaders(headers, cookieMap);
    }

    /**
     * Checks whether the given request contains an authorization header starting with the given {@code
     * authorizationHeaderPrefix}.
     *
     * @param requestContext the context of the request
     * @param authorizationHeaderPrefix the prefix of the authorization header
     * @return {@code true}, if the request contains a matching authorization header
     */
    public static boolean containsAuthorizationForPrefix(final RequestContext requestContext,
            final String authorizationHeaderPrefix) {

        final Optional<String> authorizationHeader =
                requestContext.getRequest().getHeader(HttpHeader.AUTHORIZATION.toString())
                        .map(akka.http.javadsl.model.HttpHeader::value)
                        .filter(headerValue -> headerValue.startsWith(authorizationHeaderPrefix));

        return authorizationHeader.isPresent();
    }

    public static boolean basicAuthUsernameMatches(final RequestContext requestContext, final Pattern uuidPattern) {
        final HttpRequest httpRequest = requestContext.getRequest();
        return httpRequest.getHeader(Authorization.class)
                .map(Authorization::credentials)
                .filter(credentials -> BASIC.equalsIgnoreCase(credentials.scheme()))
                .map(BasicHttpCredentials.class::cast)
                .map(BasicHttpCredentials::username)
                .filter(username -> username.matches(uuidPattern.toString()))
                .isPresent();
    }

}
