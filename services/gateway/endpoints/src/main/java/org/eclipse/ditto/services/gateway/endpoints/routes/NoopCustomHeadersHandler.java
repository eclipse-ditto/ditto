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
package org.eclipse.ditto.services.gateway.endpoints.routes;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

import akka.http.javadsl.server.RequestContext;

public final class NoopCustomHeadersHandler implements CustomHeadersHandler {

    private static final NoopCustomHeadersHandler INSTANCE = new NoopCustomHeadersHandler();

    private NoopCustomHeadersHandler() {}

    public static NoopCustomHeadersHandler getInstance() {
        return INSTANCE;
    }

    @Override
    public DittoHeaders handleCustomHeaders(final String correlationId, RequestContext requestContext,
            final RequestType requestType, final AuthorizationContext authorizationContext,
            final DittoHeaders dittoDefaultHeaders) {

        return dittoDefaultHeaders;
    }
}
