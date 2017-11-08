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

import java.util.function.Function;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;

import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Interface for authentication providers such as UNKNOWN authentication.
 */
public interface AuthenticationProvider {

    /**
     * Checks whether this provider is applicable for the request specified by {@code context}.
     * <p>Example: For Basic-Authentication, it should be checked whether a syntactically valid 'Authorization' header
     * is present. Authentication must <strong>not</strong> be checked by this method, this is the purpose of method
     * {@link #authenticate(String, Function)}.</p>
     *
     * @param context the request context
     * @return {@code true}, if this provider is applicable for the request
     */
    boolean isApplicable(final RequestContext context);

    /**
     * Returns a route which is returned when this provider is not applicable to a request.
     *
     * @return the route
     */
    Route unauthorized(String correlationId);

    /**
     * Performs the authentication and provides an {@link AuthorizationContext} to the route provided by parameter
     * {@code inner}. If the credentials are bad, the inner route will not be applied.
     *
     * @param correlationId the correlationId which will be added to the log
     * @param inner the inner route which will be wrapped with the {@link AuthorizationContext}
     * @return the inner route wrapped with the {@link AuthorizationContext}
     */
    Route authenticate(String correlationId, Function<AuthorizationContext, Route> inner);

}
