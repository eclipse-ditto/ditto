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
package org.eclipse.ditto.services.gateway.security.authentication.jwt;

import javax.annotation.concurrent.Immutable;

/**
 * An exception that is thrown when any error occurs during extraction of an authorization context by an implementation
 * of {@link org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtAuthorizationContextProvider}.
 */
@Immutable
public final class JwtAuthorizationContextProviderException extends Exception {

    public JwtAuthorizationContextProviderException(final String message) {
        super(message);
    }
}
