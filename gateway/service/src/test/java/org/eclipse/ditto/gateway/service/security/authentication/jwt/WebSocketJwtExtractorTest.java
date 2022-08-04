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
import org.eclipse.ditto.gateway.api.GatewayAuthenticationFailedException;
import org.eclipse.ditto.jwt.model.ImmutableJsonWebToken;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.jwt.model.JwtInvalidException;
import org.junit.Test;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.server.RequestContext;

/**
 * Tests {@link WebSocketJwtExtractor}.
 */
public class WebSocketJwtExtractorTest {

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder().randomCorrelationId().build();
    private static final HttpHeader VALID_AUTHORIZATION_HEADER =
            HttpHeader.parse("authorization", "Bearer " + VALID_JWT_TOKEN);
    private static final String URI_WITH_TOKEN = "http://localhost/ws/2?access_token=" + VALID_JWT_TOKEN;
    private static final String URI_WITH_INVALID_TOKEN = "http://localhost/ws/2?access_token=invalid_token";
    private static final String URI_WITHOUT_TOKEN = "http://localhost/ws/2";

    private final JwtExtractor underTest = WebSocketJwtExtractor.getInstance();

    @Test
    public void extractJwtFromParameter() {
        final RequestContext requestContext = mockRequestContext(URI_WITH_TOKEN);
        final JsonWebToken jsonWebToken = ImmutableJsonWebToken.fromToken(VALID_JWT_TOKEN);
        assertThat(underTest.apply(requestContext, DITTO_HEADERS)).contains(jsonWebToken);
    }

    @Test
    public void extractJwtFromHeader() {
        final RequestContext requestContext = mockRequestContext(URI_WITHOUT_TOKEN, VALID_AUTHORIZATION_HEADER);
        final JsonWebToken jsonWebToken = ImmutableJsonWebToken.fromToken(VALID_JWT_TOKEN);
        assertThat(underTest.apply(requestContext, DITTO_HEADERS)).contains(jsonWebToken);
    }

    @Test(expected = JwtInvalidException.class)
    public void extractInvalidJwtFromParameter() {
        final RequestContext requestContext =
                mockRequestContext(URI_WITH_INVALID_TOKEN);
        underTest.apply(requestContext, DITTO_HEADERS);
    }

    @Test
    public void extractMissingJwtFromParameter() {
        final RequestContext requestContext = mockRequestContext(URI_WITHOUT_TOKEN);
        assertThat(underTest.apply(requestContext, DITTO_HEADERS)).isEmpty();
    }

    @Test(expected = GatewayAuthenticationFailedException.class)
    public void extractMultipleJwt() {
        final RequestContext requestContext = mockRequestContext(URI_WITH_TOKEN, VALID_AUTHORIZATION_HEADER);
        underTest.apply(requestContext, DITTO_HEADERS);
    }

    protected static RequestContext mockRequestContext(final String uri, final HttpHeader... headers) {
        final HttpRequest httpRequest = HttpRequest.create(uri).addHeaders(Arrays.asList(headers));
        final RequestContext requestContext = mock(RequestContext.class);
        when(requestContext.getRequest()).thenReturn(httpRequest);
        return requestContext;
    }
}
