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
package org.eclipse.ditto.services.gateway.endpoints.routes;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

import akka.http.javadsl.server.RequestContext;

/**
 * Responsible for adding custom headers.
 */
public interface CustomHeadersHandler {

    enum RequestType {
        API,
        WS,
        SSE
    }

    /**
     * Appends custom headers to the given dittoDefaultHeaders and returns a new instance of DittoHeaders that
     * contains both of them.
     *
     * @param correlationId The correlation id.
     * @param requestContext The request context.
     * @param requestType The request type.
     * @param authorizationContext the authorization context.
     * @param dittoDefaultHeaders The headers ditto created by default.
     * @return A new instance of {@link DittoHeaders} containing both, new custom headers and ditto default headers.
     */
    DittoHeaders handleCustomHeaders(String correlationId, RequestContext requestContext, RequestType requestType,
            AuthorizationContext authorizationContext, DittoHeaders dittoDefaultHeaders);
}
