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
package org.eclipse.ditto.gateway.service.endpoints.routes;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;

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
     * Appends custom headers to the given default DittoHeaders and returns a new instance of DittoHeaders that
     * contains both of them.
     *
     * @param correlationId the correlation ID.
     * @param requestContext the request context.
     * @param requestType the request type.
     * @param dittoDefaultHeaders the headers ditto created by default.
     * @return a new future instance of {@link DittoHeaders} containing both new custom headers and default headers.
     */
    CompletionStage<DittoHeaders> handleCustomHeaders(CharSequence correlationId,
            RequestContext requestContext,
            RequestType requestType,
            DittoHeaders dittoDefaultHeaders);

}
