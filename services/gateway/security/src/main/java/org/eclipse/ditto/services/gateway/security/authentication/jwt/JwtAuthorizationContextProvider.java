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

import org.eclipse.ditto.model.base.auth.AuthorizationContext;

/**
 * Responsible for extraction of an {@link AuthorizationContext authorization context} out of a
 * {@link JsonWebToken json web token}.
 */
@FunctionalInterface
public interface JwtAuthorizationContextProvider {

    /**
     * Extracts an {@link AuthorizationContext authorization context} out of a given
     * {@link JsonWebToken json web token}.
     *
     * @param jwt the json web token that contains the information to be extracted into an authorization context.
     * @return the authorization context based on the given json web token.
     * @throws JwtAuthorizationContextProviderException when an error occurs during extraction.
     */
    AuthorizationContext getAuthorizationContext(JsonWebToken jwt) throws JwtAuthorizationContextProviderException;
}
