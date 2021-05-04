/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import java.util.concurrent.CompletableFuture;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.base.model.common.BinaryValidationResult;
import org.eclipse.ditto.jwt.model.JsonWebToken;

/**
 * Validates {@link org.eclipse.ditto.jwt.model.JsonWebToken}.
 */
@ThreadSafe
public interface JwtValidator {

    /**
     * Checks if this JSON web token is valid in terms of not expired, well formed and correctly signed.
     *
     * @param jsonWebToken the token to be validated.
     * @return A Future resolving to a {@link org.eclipse.ditto.base.model.common.BinaryValidationResult}.
     */
    CompletableFuture<BinaryValidationResult> validate(JsonWebToken jsonWebToken);

}
