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
package org.eclipse.ditto.services.gateway.security.authentication;

import akka.http.javadsl.server.RequestContext;

/**
 * Responsible for authenticating requests by a specific mechanism.
 *
 * @param <R> Type of the authentication result.
 */
public interface AuthenticationProvider<R extends AuthenticationResult> {

    /**
     * Indicates whether this authentication provider is applicable for the given request context.
     *
     * @param requestContext the request context to authenticate.
     * @return {@code true} if this authentication provider is applicable for the given request context, {@code false}
     * if not.
     */
    boolean isApplicable(RequestContext requestContext);

    /**
     * Authenticates the given {@link RequestContext request context}.
     *
     * @param requestContext the request context to authenticate.
     * @param correlationId the correlation ID of the request.
     * @return the authentication result.
     */
    R authenticate(RequestContext requestContext, CharSequence correlationId);

}
