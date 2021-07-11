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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationResult;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link DefaultJwtAuthenticationResultProvider}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class DefaultJwtAuthenticationResultProviderTest {

    @Mock
    private JwtAuthorizationSubjectsProvider authorizationSubjectsProvider;

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultJwtAuthenticationResultProvider.class,
                areImmutable(),
                provided(JwtAuthorizationSubjectsProvider.class).isAlsoImmutable());
    }

    @Test
    public void getAuthorizationContext() {
        final DefaultJwtAuthenticationResultProvider underTest =
                DefaultJwtAuthenticationResultProvider.of(authorizationSubjectsProvider);
        final JsonWebToken jsonWebToken = mock(JsonWebToken.class);
        final AuthorizationSubject myTestSubj = AuthorizationSubject.newInstance("myTestSubj");
        when(authorizationSubjectsProvider.getAuthorizationSubjects(jsonWebToken)).thenReturn(
                Collections.singletonList(myTestSubj));

        final AuthenticationResult authorizationResult =
                underTest.getAuthenticationResult(jsonWebToken, DittoHeaders.empty()).toCompletableFuture().join();

        assertThat(authorizationResult.getAuthorizationContext().getAuthorizationSubjects())
                .containsExactly(myTestSubj);
    }

}
