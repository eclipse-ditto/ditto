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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtTestConstants.EXPIRED_JWT_TOKEN;
import static org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtTestConstants.ISSUER;
import static org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtTestConstants.KEY_ID;
import static org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtTestConstants.PUBLIC_KEY;
import static org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtTestConstants.PUBLIC_KEY_2;
import static org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtTestConstants.VALID_JWT_TOKEN;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.ditto.signals.commands.base.exceptions.GatewayJwtInvalidException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.jsonwebtoken.security.SignatureException;

/**
 * Unit test for {@link AbstractJsonWebToken}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class AbstractJsonWebTokenTest {

    @Mock
    private PublicKeyProvider publicKeyProvider;

    @Test
    public void validate() throws ExecutionException, InterruptedException {
        when(publicKeyProvider.getPublicKey(ISSUER, KEY_ID)).thenReturn(
                CompletableFuture.completedFuture(Optional.of(PUBLIC_KEY)));
        final AbstractJsonWebTokenTestImplementation underTest =
                new AbstractJsonWebTokenTestImplementation("Bearer " + VALID_JWT_TOKEN);

        final BinaryValidationResult jwtValidationResult = underTest.validate(publicKeyProvider).get();

        assertThat(jwtValidationResult.isValid()).isTrue();
    }

    @Test
    public void validateFails() throws ExecutionException, InterruptedException {
        when(publicKeyProvider.getPublicKey(ISSUER, KEY_ID)).thenReturn(
                CompletableFuture.completedFuture(Optional.of(PUBLIC_KEY_2)));
        final AbstractJsonWebTokenTestImplementation underTest =
                new AbstractJsonWebTokenTestImplementation("Bearer " + VALID_JWT_TOKEN);

        final BinaryValidationResult jwtValidationResult = underTest.validate(publicKeyProvider).get();

        assertThat(jwtValidationResult.isValid()).isFalse();
        assertThat(jwtValidationResult.getReasonForInvalidity()).isInstanceOf(SignatureException.class);
    }

    @Test
    public void constructorFailsIfJwtDoesNotConsistOfThreeParts() {
        final String header = "{\"header\":\"value\"}";
        final String payload = "{\"payload\":\"foo\"}";

        final String authorizationHeader = "Bearer " + base64(header) + "." + base64(payload);

        assertThatExceptionOfType(GatewayJwtInvalidException.class)
                .isThrownBy(() -> new AbstractJsonWebTokenTestImplementation(authorizationHeader));
    }

    @Test
    public void constructor() {
        final String header = "{\"header\":\"value\"}";
        final String payload = "{\"payload\":\"foo\"}";
        final String signature = "{\"signature\":\"foo\"}";

        final String authorizationHeader = "Bearer " + base64(header) + "." + base64(payload) + "." + base64(signature);

        final AbstractJsonWebTokenTestImplementation abstractJsonWebTokenTestImplementation =
                new AbstractJsonWebTokenTestImplementation(authorizationHeader);

        assertThat(abstractJsonWebTokenTestImplementation.getHeader().toString()).isEqualTo(header);
        assertThat(abstractJsonWebTokenTestImplementation.getBody().toString()).isEqualTo(payload);
        assertThat(abstractJsonWebTokenTestImplementation.getSignature()).isEqualTo(base64(signature));
    }

    @Test
    public void checkTokenExpiration() {
        final AbstractJsonWebTokenTestImplementation expiredJsonWebToken =
                new AbstractJsonWebTokenTestImplementation("Bearer " + EXPIRED_JWT_TOKEN);

        assertThat(expiredJsonWebToken.hasExpired()).isEqualTo(true);
    }

    private static final class AbstractJsonWebTokenTestImplementation extends AbstractJsonWebToken {

        private AbstractJsonWebTokenTestImplementation(final String authorizationString) {
            super(authorizationString);
        }

        @Override
        public List<String> getSubjects() {
            return Collections.emptyList();
        }
    }

    private static String base64(final String value) {
        return new String(Base64.getEncoder().encode(value.getBytes()));
    }

}