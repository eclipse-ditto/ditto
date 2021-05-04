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
package org.eclipse.ditto.gateway.service.security.authentication;

import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.base.model.headers.DittoHeaders;

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
     * @param dittoHeaders the (potentially not completely set) DittoHeaders of the request.
     * @return the authentication result.
     */
    CompletableFuture<R> authenticate(RequestContext requestContext, DittoHeaders dittoHeaders);

}
