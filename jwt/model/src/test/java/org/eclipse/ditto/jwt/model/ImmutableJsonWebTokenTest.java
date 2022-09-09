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
package org.eclipse.ditto.jwt.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;
import java.util.Base64;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableJsonWebToken}.
 * Tokens can be decrypted at https://jwt.io
 */
public final class ImmutableJsonWebTokenTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonWebToken.class,
                areImmutable(),
                provided(AuthorizationSubject.class).areAlsoImmutable(),
                assumingFields("authorizationSubjects", "authorizationSubjectsWithPrefixes")
                        .areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableJsonWebToken.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void tryToCreateInstanceFromEmptyTokenString() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImmutableJsonWebToken.fromAuthorization(""))
                .withNoCause();
    }

    @Test
    public void tryToParseTokenFromMissingAuthorization() {
        assertThatExceptionOfType(JwtInvalidException.class)
                .isThrownBy(() -> ImmutableJsonWebToken.fromAuthorization("Authorization"))
                .withNoCause();
    }

    @Test
    public void tryToParseTokenFromInvalidAuthorization() {
        assertThatExceptionOfType(JwtInvalidException.class)
                .isThrownBy(() -> ImmutableJsonWebToken.fromAuthorization("Authorization foo"));
    }

    @Test
    public void tryToParseTokenWithMissingSignature() {
        final String header = "{\"header\":\"value\"}";
        final String payload = "{\"payload\":\"foo\"}";

        final String authorizationHeader = "Bearer " + base64(header) + "." + base64(payload);

        assertThatExceptionOfType(JwtInvalidException.class)
                .isThrownBy(() -> ImmutableJsonWebToken.fromAuthorization(authorizationHeader));
    }

    @Test
    public void parseToken() {
        final String header = "{\"header\":\"value\"}";
        final String payload = "{\"payload\":\"foo\"}";
        final String signature = "{\"signature\":\"foo\"}";

        final String authorizationHeader = "Bearer " + base64(header) + "." + base64(payload) + "." + base64(signature);

        final JsonWebToken immutableJsonWebToken = ImmutableJsonWebToken.fromAuthorization(authorizationHeader);

        assertThat(immutableJsonWebToken.getHeader().toString()).isEqualTo(header);
        assertThat(immutableJsonWebToken.getBody().toString()).isEqualTo(payload);
        assertThat(immutableJsonWebToken.getSignature()).isEqualTo(base64(signature));
    }

    @Test
    public void checkTokenExpiration() {
        final String header = "{\"header\":\"value\"}";
        final String payload = String.format("{\"exp\":%d}", Instant.now().getEpochSecond());
        final String signature = "{\"signature\":\"foo\"}";

        final String authorizationHeader = "Bearer " + base64(header) + "." + base64(payload) + "." + base64(signature);

        final JsonWebToken expiredJsonWebToken = ImmutableJsonWebToken.fromAuthorization(authorizationHeader);

        assertThat(expiredJsonWebToken.isExpired()).isTrue();
    }

    private static String base64(final String value) {
        return new String(Base64.getUrlEncoder().encode(value.getBytes()));
    }

}
