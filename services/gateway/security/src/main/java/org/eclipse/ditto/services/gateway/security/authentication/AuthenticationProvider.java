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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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
     * @return true if this authentication provider is applicable for the given request context, false if not.
     */
    boolean isApplicable(RequestContext requestContext);

    /**
     * Authenticates the given {@link RequestContext request context}.
     *
     * @param requestContext the request context to authenticate.
     * @param correlationId the correlation id of the request.
     * @param blockingDispatcher dispatcher used for blocking calls.
     * @return A future resolving to an authentication result.
     */
    CompletableFuture<R> extractAuthentication(RequestContext requestContext, String correlationId,
            Executor blockingDispatcher);
}
