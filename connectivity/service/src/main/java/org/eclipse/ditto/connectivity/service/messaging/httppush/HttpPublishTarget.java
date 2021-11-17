/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.httppush;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.service.messaging.PublishTarget;

import akka.http.javadsl.model.HttpMethod;
import akka.http.javadsl.model.HttpMethods;

/**
 * A HTTP target to which messages can be published.
 */
@Immutable
final class HttpPublishTarget implements PublishTarget {

    private static final String METHOD_SEPARATOR = ":";
    private static final HttpMethod FALLBACK_METHOD = HttpMethods.POST;

    private final HttpMethod method;
    private final String pathWithQuery;

    private HttpPublishTarget(final HttpMethod method, final String pathWithQuery) {
        this.method = method;
        this.pathWithQuery = pathWithQuery;
    }

    /**
     * @return the Akka HTTP method to use for the HTTP request.
     */
    HttpMethod getMethod() {
        return method;
    }

    /**
     * @return the complete path including optional query parameters to use for the HTTP request.
     */
    String getPathWithQuery() {
        return pathWithQuery;
    }

    /**
     * Creates a new HttpPublishTarget instance based on the passed {@code targetAddress} which is of format:
     * <pre>
     * {@code
     *     <HTTP_METHOD>:/<HTTP_PATH_INCL_PARAMS>
     * }
     * </pre>
     * E.g.: {@code POST:/api/1/foo?bar=bla}
     *
     * @param targetAddress the target address in format {@code <HTTP_METHOD>:/<HTTP_PATH_INCL_PARAMS>}
     * @return the created HttpPublishTarget
     */
    static HttpPublishTarget of(final String targetAddress) {
        final String[] methodAndPath = splitMethodAndPath(targetAddress);
        if (methodAndPath.length == 2) {
            final Optional<HttpMethod> method = HttpMethods.lookup(methodAndPath[0]);
            if (method.isPresent()) {
                return new HttpPublishTarget(method.get(), methodAndPath[1]);
            }
        }
        // Fallback: default method with entire target address as path.
        // HttpPublisherActorTest relies on the fallback even as it should never happen for valid connections.
        return new HttpPublishTarget(FALLBACK_METHOD, targetAddress);
    }

    static String[] splitMethodAndPath(final String targetAddress) {
        return targetAddress.split(METHOD_SEPARATOR, 2);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HttpPublishTarget that = (HttpPublishTarget) o;
        return Objects.equals(method, that.method) &&
                Objects.equals(pathWithQuery, that.pathWithQuery);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, pathWithQuery);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "method=" + method +
                ", pathWithQuery=" + pathWithQuery +
                "]";
    }

}
