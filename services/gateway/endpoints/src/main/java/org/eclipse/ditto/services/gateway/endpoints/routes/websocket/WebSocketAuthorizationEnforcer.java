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
package org.eclipse.ditto.services.gateway.endpoints.routes.websocket;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

import akka.http.javadsl.model.HttpRequest;

/**
 * Enforces authorization in order to establish a WebSocket connection.
 * If the authorization check is successful nothing will happen, else a
 * {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException DittoRuntimeException} is thrown.
 */
public interface WebSocketAuthorizationEnforcer {

    /**
     * Ensures that the establishment of a WebSocket connection is authorized for the given arguments.
     *
     * @param authorizationContext
     * @param request
     * @param dittoHeaders
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.model.base.exceptions.DittoRuntimeException if
     */
    void checkAuthorization(HttpRequest request, AuthorizationContext authorizationContext, DittoHeaders dittoHeaders);

}
