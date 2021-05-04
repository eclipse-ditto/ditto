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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

import akka.http.javadsl.server.Route;

/**
 * Provider for custom routes.
 * You can distinguish between routes for unauthorized access and authorized access.
 */
public interface CustomApiRoutesProvider {

    /**
     * Provides a custom route for unauthorized access.
     *
     * @param apiVersion the API version.
     * @param correlationId the correlation ID.
     * @return custom route for unauthorized access.
     */
    Route unauthorized(JsonSchemaVersion apiVersion, CharSequence correlationId);

    /**
     * Provides a custom route for authorized access.
     *
     * @param headers headers of the request.
     * @return custom route for authorized access.
     */
    Route authorized(DittoHeaders headers);

}
