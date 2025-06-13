/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import org.apache.pekko.http.javadsl.server.RequestContext;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Tests {@link JwtAuthenticationProvider}.
 */
public final class WebSocketJwtAuthenticationProviderTest extends AbstractJwtAuthenticationProviderTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private JwtAuthenticationProvider underTest;

    @Before
    public void setup() {
        knownDittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
        underTest = JwtAuthenticationProvider.newWsInstance(authenticationContextProvider, jwtValidator);
    }

    @Override
    protected JwtAuthenticationProvider getUnderTest() {
        return underTest;
    }

    @Override
    protected boolean supportsAccessTokenParameter() {
        return true;
    }

    @Override
    protected String getExpectedMissingJwtDescription() {
        return "Please provide a valid JWT in the 'Authorization' header prefixed with 'Bearer ' or as query" +
                " parameter 'access_token'.";
    }

    @Test
    public void doExtractAuthenticationWithInvalidJwtFromAccessTokenParameter() {
        final RequestContext requestContext = mockRequestContext(URI_WITH_ACCESS_TOKEN_PARAMETER);
        doExtractAuthenticationWithInvalidJwt(getUnderTest(), requestContext);
    }
}
