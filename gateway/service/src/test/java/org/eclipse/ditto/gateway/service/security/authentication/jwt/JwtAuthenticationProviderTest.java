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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests {@link JwtAuthenticationProvider}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class JwtAuthenticationProviderTest extends AbstractJwtAuthenticationProviderTest {

    private JwtAuthenticationProvider underTest;

    @Before
    public void setup() {
        knownDittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
        underTest = JwtAuthenticationProvider.newInstance(authenticationContextProvider, jwtValidator);
    }

    @Override
    protected JwtAuthenticationProvider getUnderTest() {
        return underTest;
    }

    @Override
    protected boolean supportsAccessTokenParameter() {
        return false;
    }

    @Override
    protected String getExpectedMissingJwtDescription() {
        return "Please provide a valid JWT in the authorization header prefixed with 'Bearer '.";
    }
}
