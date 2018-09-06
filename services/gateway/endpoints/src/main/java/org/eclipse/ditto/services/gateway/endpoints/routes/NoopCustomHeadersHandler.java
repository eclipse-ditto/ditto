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
