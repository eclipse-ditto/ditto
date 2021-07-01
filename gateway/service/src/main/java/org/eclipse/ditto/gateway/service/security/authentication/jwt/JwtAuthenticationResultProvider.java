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
package org.eclipse.ditto.gateway.service.security.authentication.jwt;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.jwt.model.JsonWebToken;

/**
 * Responsible for extraction of an {@link org.eclipse.ditto.gateway.service.security.authentication.AuthenticationResult} out of a
 * {@link JsonWebToken JSON web token}.
 */
@FunctionalInterface
public interface JwtAuthenticationResultProvider {

    /**
     * Extracts an {@code AuthenticationResult} out of a given JsonWebToken.
     *
     * @param jwt the JSON web token that contains the information to be extracted into an authorization context.
     * @param dittoHeaders the DittoHeaders to use for the extracted authentication result.
     * @return the authentication result based on the given JSON web token.
     * @throws NullPointerException if any argument is {@code null}.
     */
    CompletionStage<JwtAuthenticationResult> getAuthenticationResult(JsonWebToken jwt, DittoHeaders dittoHeaders);

}
