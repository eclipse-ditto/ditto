/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.jwt;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;

/**
 * Tests both {@link JjwtSerializer} and {@link JjwtDeserializer} by using the JJWT API.
 */
public class JjwtDeserializerTest {

    private static final String KNOWN_ISS = "Ditto";
    private static final String KNOWN_SUB = "some-user";
    private static final Date KNOWN_EXP = Date.from(Instant.now().plusSeconds(5));

    @Test
    public void foo() {
        final Map<String, Object> claims = new HashMap<>();
        claims.put(Claims.ISSUER, KNOWN_ISS);
        claims.put(Claims.SUBJECT, KNOWN_SUB);
        final String compact = Jwts.builder().serializeToJsonWith(JjwtSerializer.getInstance())
                .setClaims(claims)
                .setExpiration(KNOWN_EXP)
                .compact();

        final Jwt jwt = Jwts.parser().deserializeJsonWith(JjwtDeserializer.getInstance())
                .parse(compact);

        Assertions.assertThat(jwt.getBody()).isInstanceOf(Claims.class);
        Assertions.assertThat(((Claims) jwt.getBody()).get(Claims.ISSUER)).isEqualTo(KNOWN_ISS);
        Assertions.assertThat(((Claims) jwt.getBody()).get(Claims.SUBJECT)).isEqualTo(KNOWN_SUB);
        Assertions.assertThat(((Claims) jwt.getBody()).get(Claims.EXPIRATION))
                .isEqualTo((int) (KNOWN_EXP.getTime() / 1000L));
    }
}
