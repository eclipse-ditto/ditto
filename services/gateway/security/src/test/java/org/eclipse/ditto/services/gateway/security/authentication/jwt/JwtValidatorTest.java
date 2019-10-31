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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.ditto.model.base.common.BinaryValidationResult;
import org.eclipse.ditto.model.jwt.ImmutableJsonWebToken;
import org.eclipse.ditto.model.jwt.JsonWebToken;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.jsonwebtoken.ExpiredJwtException;

/**
 * Unit test for {@link DefaultJwtValidator}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class JwtValidatorTest {

    private static final JsonWebToken VALID_JSON_WEB_TOKEN =
            ImmutableJsonWebToken.fromAuthorization("Bearer " + JwtTestConstants.VALID_JWT_TOKEN);

    private static final JsonWebToken INVALID_JSON_WEB_TOKEN =
            ImmutableJsonWebToken.fromAuthorization("Bearer " + JwtTestConstants.EXPIRED_JWT_TOKEN);

    @Mock
    private PublicKeyProvider publicKeyProvider;

    @Test
    public void validate() throws ExecutionException, InterruptedException {
        when(publicKeyProvider.getPublicKey(JwtTestConstants.ISSUER, JwtTestConstants.KEY_ID)).thenReturn(
                CompletableFuture.completedFuture(Optional.of(JwtTestConstants.PUBLIC_KEY)));

        final JwtValidator underTest = DefaultJwtValidator.of(publicKeyProvider);

        final BinaryValidationResult jwtValidationResult = underTest.validate(VALID_JSON_WEB_TOKEN).get();

        assertThat(jwtValidationResult.isValid()).isTrue();
    }

    @Test
    public void validateFails() throws ExecutionException, InterruptedException {
        when(publicKeyProvider.getPublicKey(JwtTestConstants.ISSUER, JwtTestConstants.KEY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(JwtTestConstants.PUBLIC_KEY)));

        final JwtValidator underTest = DefaultJwtValidator.of(publicKeyProvider);

        final BinaryValidationResult jwtValidationResult = underTest.validate(INVALID_JSON_WEB_TOKEN).get();

        assertThat(jwtValidationResult.isValid()).isFalse();
        assertThat(jwtValidationResult.getReasonForInvalidity()).isInstanceOf(ExpiredJwtException.class);
    }

}