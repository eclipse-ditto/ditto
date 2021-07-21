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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Date;

import javax.annotation.concurrent.Immutable;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * Test constants for JWT tests.
 */
@Immutable
final class JwtTestConstants {

    static final String VALID_JWT_TOKEN;
    static final String UNSIGNED_JWT_TOKEN;
    static final String EXPIRED_JWT_TOKEN;
    static final String VALID_NBF_AHEAD_OF_TIME_JWT_TOKEN;
    static final String INVALID_NBF_AHEAD_OF_TIME_JWT_TOKEN;
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
            UNSIGNED_JWT_TOKEN = createUnsignedJwt();
            EXPIRED_JWT_TOKEN = createExpiredJwt();
            VALID_NBF_AHEAD_OF_TIME_JWT_TOKEN = createNotBeforeAheadOfTimeJwt(Date.from(Instant.now().plusSeconds(10)));
            INVALID_NBF_AHEAD_OF_TIME_JWT_TOKEN =
                    createNotBeforeAheadOfTimeJwt(Date.from(Instant.now().plusSeconds(30)));
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

    private static String createUnsignedJwt() {
        return Jwts.builder()
                .setHeaderParam("kid", KEY_ID)
                .setIssuer(ISSUER)
                .compact();
    }

    private static String createExpiredJwt() {
        return Jwts.builder()
                .setHeaderParam("kid", KEY_ID)
                .setIssuer(ISSUER)
                .setExpiration(Date.from(Instant.now().minusSeconds(10)))
                .signWith(PRIVATE_KEY, SignatureAlgorithm.RS256)
                .compact();
    }

    private static String createNotBeforeAheadOfTimeJwt(final Date nbf) {
        return Jwts.builder()
                .setHeaderParam("kid", KEY_ID)
                .setIssuer(ISSUER)
                .setNotBefore(nbf)
                .signWith(PRIVATE_KEY, SignatureAlgorithm.RS256)
                .compact();
    }

}
