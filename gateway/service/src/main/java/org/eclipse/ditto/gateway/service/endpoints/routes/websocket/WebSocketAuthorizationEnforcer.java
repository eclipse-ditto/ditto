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
package org.eclipse.ditto.gateway.service.endpoints.routes.websocket;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;

/**
 * Enforces authorization in order to establish a WebSocket connection.
 * If the authorization check is successful the headers are given back, possibly with new information, else a
 * {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException DittoRuntimeException} is thrown.
 */
public interface WebSocketAuthorizationEnforcer {

    /**
     * Ensures that the establishment of a WebSocket connection is authorized for the given DittoHeaders of the initial
     * WebSocket request.
     *
     * @param dittoHeaders the DittoHeaders containing already gathered context information.
     * @throws NullPointerException if any argument is {@code null}.
     * @return a successful future of headers containing new information if the check succeeded,
     * or a failed future if the check failed.
     */
    CompletionStage<DittoHeaders> checkAuthorization(DittoHeaders dittoHeaders);

}
