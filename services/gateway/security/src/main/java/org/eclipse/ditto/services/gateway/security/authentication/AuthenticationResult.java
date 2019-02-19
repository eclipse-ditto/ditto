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

import org.eclipse.ditto.model.base.auth.AuthorizationContext;

/**
 * The result of an authentication.
 */
public interface AuthenticationResult {

    /**
     * Indicates whether the authentication was successful.
     *
     * @return True if the authentication was succussful, false if not.
     */
    boolean isSuccess();

    /**
     * Call this method only if {@link #isSuccess()} is true.
     *
     * @return the authorization context if the authentication was successful.
     * @throws java.lang.RuntimeException the reason of failure if this method is called when {@link #isSuccess()} is
     * false.
     */
    AuthorizationContext getAuthorizationContext();

    /**
     * Call this method only if {@link #isSuccess()} is false.
     *
     * @return the reason why the authentication failed.
     * @throws java.lang.IllegalStateException if this methods is called when {@link #isSuccess()} is true.
     */
    Throwable getReasonOfFailure();
}
