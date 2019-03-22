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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

import javax.annotation.concurrent.Immutable;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * Test constants for JWT tests.
 */
@Immutable
final class JwtTestConstants {

    static final String VALID_JWT_TOKEN;
    static final PublicKey PUBLIC_KEY_2;

    static final String KEY_ID = "pFXsMxGhnXJgzg9aO9xYUTYegCP4XsnuGhQEeQaAQrI";
    static final String ISSUER = "https://some-issuer.org/auth/realms/iot-suite";

    static final PublicKey PUBLIC_KEY;
    static final PrivateKey PRIVATE_KEY;

    static {
        try {
            final KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);

            final KeyPair keyPair = keyGen.generateKeyPair();
            PUBLIC_KEY = keyPair.getPublic();
            PRIVATE_KEY = keyPair.getPrivate();

            final KeyPair keyPair2 = keyGen.generateKeyPair();
            PUBLIC_KEY_2 = keyPair2.getPublic();

            VALID_JWT_TOKEN = createJwt();
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String createJwt() {
        return Jwts.builder()
                .setHeaderParam("kid", KEY_ID)
                .setIssuer(ISSUER)
                .signWith(PRIVATE_KEY, SignatureAlgorithm.RS256)
                .compact();
    }

}
