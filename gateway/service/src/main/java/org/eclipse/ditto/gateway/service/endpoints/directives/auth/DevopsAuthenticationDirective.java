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

import akka.http.javadsl.server.Route;


/**
 * Authentication directives that can be used to secure devops and status resources.
 */
@FunctionalInterface
public interface DevopsAuthenticationDirective {

    Route authenticateDevOps(final String realm, final Route inner);

}
