/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.directives.auth;

import org.apache.pekko.http.javadsl.server.Route;
import org.eclipse.ditto.base.model.headers.DittoHeaders;


/**
 * Authentication directives that can be used to secure devops and status resources.
 */
@FunctionalInterface
public interface DevopsAuthenticationDirective {

    /**
     * Authenticates the devops resources with the chosen authentication method.
     *
     * @param realm the realm to apply.
     * @param dittoHeaders the DittoHeaders to use for logging.
     * @param inner the inner route, which will be performed on successful authentication.
     * @return the inner route wrapped with authentication.
     */
    Route authenticateDevOps(String realm, DittoHeaders dittoHeaders, Route inner);

}
