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
package org.eclipse.ditto.services.gateway.security.authentication;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;

/**
 * The result of an authentication.
 */
public interface AuthenticationResult {

    /**
     * Indicates whether the authentication was successful.
     *
     * @return {@code true} if the authentication was successful, {@code false} if not.
     */
    boolean isSuccess();

    /**
     * Call this method only if {@link #isSuccess()} evaluates to {@code true}.
     *
     * @return the authorization context if the authentication was successful.
     * @throws java.lang.RuntimeException the reason of failure if this method is called when {@link #isSuccess()}
     * evaluates to {@code false}.
     */
    AuthorizationContext getAuthorizationContext();

    /**
     * Call this method only if {@link #isSuccess()} evaluates to {@code false}.
     *
     * @return the reason why the authentication failed.
     * @throws java.lang.IllegalStateException if this methods is called when {@link #isSuccess()} evaluates to
     * {@code true}.
     */
    Throwable getReasonOfFailure();

}
