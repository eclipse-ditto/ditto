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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtTestConstants.VALID_JWT_TOKEN;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.jwt.model.ImmutableJsonWebToken;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.jwt.model.JwtInvalidException;
import org.junit.Test;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.server.RequestContext;

/**
 * Tests {@link DefaultJwtExtractor}.
 */
public class DefaultJwtExtractorTest {

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder().randomCorrelationId().build();
    private static final HttpHeader VALID_AUTHORIZATION_HEADER =
            HttpHeader.parse("authorization", "Bearer " + VALID_JWT_TOKEN);
    private static final HttpHeader INVALID_AUTHORIZATION_HEADER =
            HttpHeader.parse("authorization", "Basic " + VALID_JWT_TOKEN);

    private final JwtExtractor underTest = DefaultJwtExtractor.getInstance();

    @Test
    public void extractJwtFromHeader() {
        final RequestContext requestContext =
                mockRequestContext(VALID_AUTHORIZATION_HEADER);
        final JsonWebToken jsonWebToken = ImmutableJsonWebToken.fromToken(VALID_JWT_TOKEN);
        assertThat(underTest.apply(requestContext, DITTO_HEADERS)).contains(jsonWebToken);
    }

    @Test(expected = JwtInvalidException.class)
    public void extractInvalidJwtFromHeader() {
        final RequestContext requestContext = mockRequestContext(INVALID_AUTHORIZATION_HEADER);
        underTest.apply(requestContext, DITTO_HEADERS);
    }

    @Test
    public void extractMissingJwtFromHeader() {
        final RequestContext requestContext = mockRequestContext();
        assertThat(underTest.apply(requestContext, DITTO_HEADERS)).isEmpty();
    }

    private static RequestContext mockRequestContext(final HttpHeader... httpHeaders) {
        final HttpRequest httpRequest = HttpRequest.create().addHeaders(Arrays.asList(httpHeaders));

        final RequestContext requestContext = mock(RequestContext.class);
        when(requestContext.getRequest()).thenReturn(httpRequest);
        return requestContext;
    }

}
