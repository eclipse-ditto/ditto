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
package org.eclipse.ditto.services.gateway.security.authentication.jwt;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;

/**
 * Responsible for extraction of an {@link AuthorizationContext authorization context} out of a
 * {@link JsonWebToken JSON web token}.
 */
@FunctionalInterface
public interface JwtAuthorizationContextProvider {

    /**
     * Extracts an {@link AuthorizationContext authorization context} out of a given
     * {@link JsonWebToken JSON web token}.
     *
     * @param jwt the JSON web token that contains the information to be extracted into an authorization context.
     * @return the authorization context based on the given JSON web token.
     * @throws NullPointerException if {@code jwt} is {@code null}.
     */
    AuthorizationContext getAuthorizationContext(JsonWebToken jwt);

}
