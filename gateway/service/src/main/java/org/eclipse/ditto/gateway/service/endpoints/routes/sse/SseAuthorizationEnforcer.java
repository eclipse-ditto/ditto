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
package org.eclipse.ditto.gateway.service.endpoints.routes.sse;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;

import akka.http.javadsl.server.RequestContext;

/**
 * Enforces authorization in order to establish a SSE connection.
 * If the authorization check is successful nothing will happen, else a
 * {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException DittoRuntimeException} is thrown.
 */
public interface SseAuthorizationEnforcer {

    /**
     * Ensures that the establishment of a SSE connection is authorized for the given arguments.
     *
     * @param requestContext the context of the HTTP request for opening the connection.
     * @param dittoHeaders the DittoHeaders with authentication information for opening the connection.
     * @throws NullPointerException if any argument is {@code null}.
     * @return a successful future if validation succeeds or a failed future if validation fails.
     */
    CompletionStage<Void> checkAuthorization(RequestContext requestContext, DittoHeaders dittoHeaders);

}
