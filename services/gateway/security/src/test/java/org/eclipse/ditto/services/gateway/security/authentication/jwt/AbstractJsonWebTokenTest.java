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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtTestConstants.ISSUER;
import static org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtTestConstants.KEY_ID;
import static org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtTestConstants.PUBLIC_KEY;
import static org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtTestConstants.PUBLIC_KEY_2;
import static org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtTestConstants.VALID_JWT_TOKEN;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.jsonwebtoken.security.SignatureException;

/**
 * Tests {@link AbstractJsonWebToken}.
 */
@RunWith(MockitoJUnitRunner.class)
public class AbstractJsonWebTokenTest {

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

    private static class AbstractJsonWebTokenTestImplementation extends AbstractJsonWebToken {

        private AbstractJsonWebTokenTestImplementation(final String authorizationString) {
            super(authorizationString);
        }

        @Override
        public List<String> getSubjects() {
            return Collections.emptyList();
        }
    }

}