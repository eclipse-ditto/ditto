/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.time.Instant;

import org.eclipse.ditto.connectivity.service.messaging.signing.Signing;

import akka.NotUsed;
import akka.http.javadsl.model.HttpRequest;
import akka.stream.javadsl.Source;

/**
 * Functional interface for signing an HTTP request before sending it.
 *
 * @since 2.1.0
 */
@FunctionalInterface
public interface HttpRequestSigning extends Signing {

    /**
     * Sign an HTTP request.
     *
     * @param request The HTTP request.
     * @param timestamp Timestamp to include in the signature.
     * @return A singleton source of the signed request.
     */
    Source<HttpRequest, NotUsed> sign(HttpRequest request, Instant timestamp);

    /**
     * Sign an HTTP request at the present time.
     *
     * @param request TThe HTTP request.
     * @return A singleton source of the signed request.
     */
    default Source<HttpRequest, NotUsed> sign(final HttpRequest request) {
        return sign(request, Instant.now());
    }

}
