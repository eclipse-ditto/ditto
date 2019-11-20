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

import java.util.concurrent.CompletionStage;

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
     * @return A new future instance of {@link DittoHeaders} containing both new custom headers and default headers.
     */
    CompletionStage<DittoHeaders> handleCustomHeaders(String correlationId, RequestContext requestContext,
            RequestType requestType,
            AuthorizationContext authorizationContext, DittoHeaders dittoDefaultHeaders);
}
