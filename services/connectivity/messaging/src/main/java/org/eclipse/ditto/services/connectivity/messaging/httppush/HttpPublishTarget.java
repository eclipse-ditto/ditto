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
package org.eclipse.ditto.services.connectivity.messaging.httppush;

import org.eclipse.ditto.services.connectivity.messaging.PublishTarget;

import akka.http.javadsl.model.HttpMethod;
import akka.http.javadsl.model.HttpMethods;

final class HttpPublishTarget implements PublishTarget {

    private static final String METHOD_SEPARATOR = ":/";
    private static final String PATH_SEPARATOR = "/";

    private final HttpMethod method;
    private final String path;

    private HttpPublishTarget(final HttpMethod method, final String path) {
        this.method = method;
        this.path = path;
    }

    HttpMethod getMethod() {
        return method;
    }

    String[] getPathSegments() {
        return path.split(PATH_SEPARATOR);
    }

    static HttpPublishTarget of(final String targetAddress) {
        final String[] methodAndPath = splitMethodAndPath(targetAddress);
        if (methodAndPath.length == 2) {
            final HttpMethod method = HttpMethods.lookup(methodAndPath[0]).orElse(HttpMethods.POST);
            return new HttpPublishTarget(method, methodAndPath[1]);
        } else {
            // validator should rule this out
            return new HttpPublishTarget(HttpMethods.POST, targetAddress);
        }
    }

    static String[] splitMethodAndPath(final String targetAddress) {
        return targetAddress.split(METHOD_SEPARATOR, 2);
    }
}
