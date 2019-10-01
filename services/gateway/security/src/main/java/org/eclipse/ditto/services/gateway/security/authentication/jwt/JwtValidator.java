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

package org.eclipse.ditto.services.gateway.security.authentication.jwt;

import java.util.concurrent.CompletableFuture;

public class JwtValidator {

    private static JwtValidator instance;

    private final PublicKeyProvider publicKeyProvider;

    private JwtValidator(final PublicKeyProvider publicKeyProvider) {
        this.publicKeyProvider = publicKeyProvider;
    }

    public static JwtValidator getInstance(final PublicKeyProvider publicKeyProvider){
        if (instance == null) {
            instance = new JwtValidator(publicKeyProvider);
        }
        return instance;
    }

    public CompletableFuture<BinaryValidationResult> validate(final JsonWebToken jwt) {
        return jwt.validate(publicKeyProvider);
    }
}
