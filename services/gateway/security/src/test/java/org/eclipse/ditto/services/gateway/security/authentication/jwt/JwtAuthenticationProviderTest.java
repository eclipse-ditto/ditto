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
import static org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtTestConstants.VALID_JWT_TOKEN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.BinaryValidationResult;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.jwt.JsonWebToken;
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationResult;
import org.eclipse.ditto.services.gateway.security.authentication.DefaultAuthenticationResult;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.server.RequestContext;

/**
 * Tests {@link JwtAuthenticationProvider}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class JwtAuthenticationProviderTest {

    private static final HttpHeader VALID_AUTHORIZATION_HEADER =
            HttpHeader.parse("authorization", "Bearer " + VALID_JWT_TOKEN);
    private static final HttpHeader INVALID_AUTHORIZATION_HEADER =
            HttpHeader.parse("authorization", "Basic " + VALID_JWT_TOKEN);
    private JwtAuthenticationProvider underTest;

    @Mock
    private JwtAuthorizationContextProvider authenticationContextProvider;

    @Mock
    private JwtValidator jwtValidator;

    @Before
    public void setup() {
        underTest = JwtAuthenticationProvider.newInstance(authenticationContextProvider, jwtValidator);
    }

    @Test
    public void isApplicable() {
        final RequestContext requestContext = mockRequestContext(VALID_AUTHORIZATION_HEADER);

        assertThat(underTest.isApplicable(requestContext)).isTrue();
    }

    @Test
    public void isApplicableWithoutAuthorizationHeader() {
        final RequestContext requestContext = mockRequestContext();

        assertThat(underTest.isApplicable(requestContext)).isFalse();
    }

    @Test
    public void isApplicableWithInvalidAuthorizationHeader() {
        final RequestContext requestContext = mockRequestContext(INVALID_AUTHORIZATION_HEADER);

        assertThat(underTest.isApplicable(requestContext)).isFalse();
    }

    @Test
    public void doExtractAuthentication() {
        when(jwtValidator.validate(any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(BinaryValidationResult.valid()));
        when(authenticationContextProvider.getAuthorizationContext(any(JsonWebToken.class))).thenReturn(
                AuthorizationContext.newInstance(AuthorizationSubject.newInstance("myAuthSubj")));
        final RequestContext requestContext = mockRequestContext(VALID_AUTHORIZATION_HEADER);
        final String correlationId = getRandomUuid();
        final DefaultAuthenticationResult authenticationResult =
                underTest.tryToAuthenticate(requestContext, correlationId);

        assertThat(authenticationResult.isSuccess()).isTrue();
    }

    @Test
    public void doExtractAuthenticationWhenAuthorizationContextProviderErrors() {
        when(jwtValidator.validate(any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(BinaryValidationResult.valid()));
        when(authenticationContextProvider.getAuthorizationContext(any(JsonWebToken.class)))
                .thenThrow(new RuntimeException("Something happened"));
        final RequestContext requestContext = mockRequestContext(VALID_AUTHORIZATION_HEADER);
        final String correlationId = getRandomUuid();
        final DefaultAuthenticationResult authenticationResult =
                underTest.tryToAuthenticate(requestContext, correlationId);

        verify(authenticationContextProvider).getAuthorizationContext(any(JsonWebToken.class));
        assertThat(authenticationResult.isSuccess()).isFalse();
        assertThat(authenticationResult.getReasonOfFailure())
                .isExactlyInstanceOf(GatewayAuthenticationFailedException.class);
        final GatewayAuthenticationFailedException reasonOfFailure =
                (GatewayAuthenticationFailedException) authenticationResult.getReasonOfFailure();
        assertThat(reasonOfFailure).hasMessage("The JWT could not be verified.");
        assertThat(reasonOfFailure.getDescription()).contains("Something happened");
        assertThat(reasonOfFailure.getDittoHeaders().getCorrelationId()).contains(correlationId);
    }

    @Test
    public void doExtractAuthenticationWithMissingJwt() {
        final RequestContext requestContext = mockRequestContext();
        final String correlationId = getRandomUuid();

        final DefaultAuthenticationResult authenticationResult =
                underTest.tryToAuthenticate(requestContext, correlationId);

        assertThat(authenticationResult.isSuccess()).isFalse();
        assertThat(authenticationResult.getReasonOfFailure()).isInstanceOf(GatewayAuthenticationFailedException.class);
        final DittoRuntimeException reasonOfFailureDre =
                (DittoRuntimeException) authenticationResult.getReasonOfFailure();
        assertThat(reasonOfFailureDre).hasMessage("The JWT was missing.");
        assertThat(reasonOfFailureDre.getErrorCode()).isEqualTo("gateway:authentication.failed");
        assertThat(reasonOfFailureDre.getStatusCode()).isEqualTo(HttpStatusCode.UNAUTHORIZED);
        assertThat(reasonOfFailureDre.getDittoHeaders().getCorrelationId()).contains(correlationId);
        assertThat(reasonOfFailureDre.getDescription()).contains(
                "Please provide a valid JWT in the authorization header prefixed with 'Bearer '");
    }

    @Test
    public void doExtractAuthenticationWithInvalidJwt() {
        when(jwtValidator.validate(any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        BinaryValidationResult.invalid(new IllegalStateException("foo"))));
        final RequestContext requestContext = mockRequestContext(VALID_AUTHORIZATION_HEADER);
        final String correlationId = getRandomUuid();

        final AuthenticationResult authenticationResult =
                underTest.tryToAuthenticate(requestContext, correlationId);

        assertThat(authenticationResult.isSuccess()).isFalse();
        assertThat(authenticationResult.getReasonOfFailure()).isInstanceOf(GatewayAuthenticationFailedException.class);
        final DittoRuntimeException reasonOfFailureDre =
                (DittoRuntimeException) authenticationResult.getReasonOfFailure();
        assertThat(reasonOfFailureDre).hasMessage("The JWT could not be verified.");
        assertThat(reasonOfFailureDre.getErrorCode()).isEqualTo("gateway:authentication.failed");
        assertThat(reasonOfFailureDre.getStatusCode()).isEqualTo(HttpStatusCode.UNAUTHORIZED);
        assertThat(reasonOfFailureDre.getDittoHeaders().getCorrelationId()).contains(correlationId);
    }

    @Test
    public void toFailedAuthenticationResultExtractsDittoRuntimeExceptionFromCause() {
        final String correlationId = getRandomUuid();
        final DittoRuntimeException dre =
                DittoRuntimeException.newBuilder("none", HttpStatusCode.INTERNAL_SERVER_ERROR).build();
        final IllegalStateException illegalStateException = new IllegalStateException("notExpected", dre);

        final Throwable reasonOfFailure =
                underTest.toFailedAuthenticationResult(illegalStateException, correlationId).getReasonOfFailure();

        assertThat(reasonOfFailure).isEqualTo(dre);
    }

    @Test
    public void toFailedAuthenticationResult() {
        final String correlationId = getRandomUuid();
        final DittoRuntimeException dre =
                DittoRuntimeException.newBuilder("none", HttpStatusCode.INTERNAL_SERVER_ERROR).build();

        final AuthenticationResult authenticationResult = underTest.toFailedAuthenticationResult(dre, correlationId);

        assertThat(authenticationResult.getReasonOfFailure()).isEqualTo(dre);
    }

    @Test
    public void getType() {
        assertThat(underTest.getType()).isEqualTo("JWT");
    }

    private static String getRandomUuid() {
        return UUID.randomUUID().toString();
    }

    private static RequestContext mockRequestContext(final HttpHeader... httpHeaders) {
        final HttpRequest httpRequest = HttpRequest.create().addHeaders(Arrays.asList(httpHeaders));

        final RequestContext requestContext = mock(RequestContext.class);
        when(requestContext.getRequest()).thenReturn(httpRequest);
        return requestContext;
    }

}