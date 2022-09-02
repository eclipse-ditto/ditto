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
package org.eclipse.ditto.internal.utils.jwt;

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
public final class JjwtDeserializerTest {

    private static final String KNOWN_ISS = "Ditto";
    private static final String KNOWN_SUB = "some-user";
    private static final Date KNOWN_EXP = Date.from(Instant.now().plusSeconds(5));

    @Test
    public void foo() {
        final Map<String, Object> claims = new HashMap<>();
        claims.put(Claims.ISSUER, KNOWN_ISS);
        claims.put(Claims.SUBJECT, KNOWN_SUB);
        final String compact = Jwts.builder()
                .serializeToJsonWith(JjwtSerializer.getInstance())
                .setClaims(claims)
                .setExpiration(KNOWN_EXP)
                .compact();

        final Jwt jwt = Jwts.parserBuilder()
                .deserializeJsonWith(JjwtDeserializer.getInstance())
                .build()
                .parse(compact);

        final Object jwtBody = jwt.getBody();

        Assertions.assertThat(jwtBody).isInstanceOf(Claims.class);
        Assertions.assertThat(((Claims) jwtBody)).containsEntry(Claims.ISSUER, KNOWN_ISS)
                .containsEntry(Claims.SUBJECT, KNOWN_SUB)
                .containsEntry(Claims.EXPIRATION, (int) (KNOWN_EXP.getTime() / 1000L));
    }

}
