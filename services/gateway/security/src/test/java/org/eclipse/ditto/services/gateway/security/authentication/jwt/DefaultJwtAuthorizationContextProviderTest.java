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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link DefaultJwtAuthorizationContextProvider}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class DefaultJwtAuthorizationContextProviderTest {

    @Mock
    private JwtAuthorizationSubjectsProvider authorizationSubjectsProvider;

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultJwtAuthorizationContextProvider.class,
                areImmutable(),
                provided(JwtAuthorizationSubjectsProvider.class).isAlsoImmutable());
    }

    @Test
    public void getAuthorizationContext() {
        final DefaultJwtAuthorizationContextProvider underTest =
                DefaultJwtAuthorizationContextProvider.getInstance(authorizationSubjectsProvider);
        final JsonWebToken jsonWebToken = mock(JsonWebToken.class);
        final AuthorizationSubject myTestSubj = AuthorizationSubject.newInstance("myTestSubj");
        when(authorizationSubjectsProvider.getAuthorizationSubjects(jsonWebToken)).thenReturn(
                Collections.singletonList(myTestSubj));

        final AuthorizationContext authorizationContext = underTest.getAuthorizationContext(jsonWebToken);

        assertThat(authorizationContext.getAuthorizationSubjects()).containsExactly(myTestSubj);
    }

}