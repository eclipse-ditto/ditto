/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.endpoints.directives.auth;

import java.util.Optional;
import java.util.function.Function;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;

import akka.http.javadsl.server.Route;

/**
 * Interface for optional authentication directives.
 */
public interface AuthenticationOptionalDirective {

    /**
     * Performs the authentication, if credentials are provided, and provides an {@link AuthorizationContext}
     * to the route provided by parameter {@code inner}, if authentication was successful. If no credentials are
     * provided, an empty {@link Optional} is provided to the inner route. If the credentials are
     * bad, the inner route will not be applied.
     *
     * @param correlationId the correlationId which will be added to the log
     * @param inner the inner route which will be wrapped with the {@link AuthorizationContext}
     * @return the inner route wrapped with the {@link AuthorizationContext}
     */
    Route authenticateOptional(String correlationId, Function<Optional<AuthorizationContext>,
            Route> inner);
}
