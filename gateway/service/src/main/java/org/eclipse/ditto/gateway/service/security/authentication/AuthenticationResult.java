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

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.headers.DittoHeaders;

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
     * Returns the authorization context if the authentication was successful.
     * <em>Call this method only if {@link #isSuccess()} evaluates to {@code true}.</em>
     *
     * @return the authorization context if the authentication was successful.
     * @throws RuntimeException the reason of failure if this method is called when {@link #isSuccess()}
     * evaluates to {@code false}.
     */
    AuthorizationContext getAuthorizationContext();

    /**
     * Returns the DittoHeaders of the either succeeded or failed AuthenticationResult.
     *
     * @return the DittoHeaders of this authentication result.
     * @since 1.1.0
     */
    DittoHeaders getDittoHeaders();

    /**
     * Returns he reason why the authentication failed.
     * <em>Call this method only if {@link #isSuccess()} evaluates to {@code false}.</em>
     *
     * @return the reason why the authentication failed.
     * @throws IllegalStateException if this methods is called when {@link #isSuccess()} evaluates to
     * {@code true}.
     */
    Throwable getReasonOfFailure();

}
