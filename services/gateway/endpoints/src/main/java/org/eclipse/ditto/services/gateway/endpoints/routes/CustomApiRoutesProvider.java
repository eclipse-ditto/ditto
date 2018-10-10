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

import org.eclipse.ditto.model.base.headers.DittoHeaders;

import akka.http.javadsl.server.Route;

/**
 * Provider for custom routes. You can distinguish between routes for unauthorized access and authorized access.
 */
public interface CustomApiRoutesProvider {

    /**
     * Provides a custom route for unauthorized access.
     *
     * @param apiVersion The api version.
     * @param correlationId The correlation id.
     * @return Custom route for unauthorized access.
     */
    Route unauthorized(Integer apiVersion, String correlationId);

    /**
     * Provides a custom route for authorized access.
     *
     * @param headers headers of the request.
     * @return Custom route for authorized access.
     */
    Route authorized(DittoHeaders headers);

}
