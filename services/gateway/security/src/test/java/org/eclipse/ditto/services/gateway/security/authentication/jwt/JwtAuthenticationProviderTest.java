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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
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
public class JwtAuthenticationProviderTest {

    private static final HttpHeader VALID_AUTHORIZATION_HEADER =
            HttpHeader.parse("authorization", "Bearer " + VALID_JWT_TOKEN);
    private static final HttpHeader INVALID_AUTHORIZATION_HEADER =
            HttpHeader.parse("authorization", "Basic " + VALID_JWT_TOKEN);
    private JwtAuthenticationProvider underTest;

    @Mock
    private PublicKeyProvider publicKeyProvider;

    @Mock
    private JwtAuthorizationContextProvider authenticationContextProvider;

    private final Executor messageDispatcher;

    public JwtAuthenticationProviderTest() {
        this.messageDispatcher = Executors.newFixedThreadPool(8);
    }

    @Before
    public void setup() {
        this.underTest =
                JwtAuthenticationProvider.getInstance(publicKeyProvider, authenticationContextProvider);
    }

    @Test
    public void isApplicable() {
        final RequestContext requestContext = mockRequestContext(VALID_AUTHORIZATION_HEADER);

        final boolean applicable = this.underTest.isApplicable(requestContext);

        assertThat(applicable).isTrue();
    }

    @Test
    public void isApplicableWithoutAuthorizationHeader() {
        final RequestContext requestContext = mockRequestContext();

        final boolean applicable = this.underTest.isApplicable(requestContext);

        assertThat(applicable).isFalse();
    }

    @Test
    public void isApplicableWithInvalidAuthorizationHeader() {
        final RequestContext requestContext = mockRequestContext(INVALID_AUTHORIZATION_HEADER);

        final boolean applicable = this.underTest.isApplicable(requestContext);

        assertThat(applicable).isFalse();
    }

    @Test
    public void doExtractAuthentication()
            throws ExecutionException, InterruptedException, JwtAuthorizationContextProviderException {
        when(publicKeyProvider.getPublicKey(ISSUER, KEY_ID)).thenReturn(
                CompletableFuture.completedFuture(Optional.of(PUBLIC_KEY)));
        when(authenticationContextProvider.getAuthorizationContext(any(JsonWebToken.class))).thenReturn(
                AuthorizationContext.newInstance(AuthorizationSubject.newInstance("myAuthSubj")));
        final RequestContext requestContext = mockRequestContext(VALID_AUTHORIZATION_HEADER);
        final String correlationId = UUID.randomUUID().toString();
        final DefaultAuthenticationResult authenticationResult =
                underTest.doExtractAuthentication(requestContext, correlationId, messageDispatcher)
                        .get();

        assertThat(authenticationResult.isSuccess()).isTrue();
    }

    @Test
    public void doExtractAuthenticationWhenAuthorizationContextProviderErrors()
            throws ExecutionException, InterruptedException, JwtAuthorizationContextProviderException {
        when(publicKeyProvider.getPublicKey(ISSUER, KEY_ID)).thenReturn(
                CompletableFuture.completedFuture(Optional.of(PUBLIC_KEY)));
        when(authenticationContextProvider.getAuthorizationContext(any(JsonWebToken.class)))
                .thenThrow(new JwtAuthorizationContextProviderException("Something happened"));
        final RequestContext requestContext = mockRequestContext(VALID_AUTHORIZATION_HEADER);
        final String correlationId = UUID.randomUUID().toString();
        final DefaultAuthenticationResult authenticationResult =
                underTest.doExtractAuthentication(requestContext, correlationId, messageDispatcher)
                        .get();

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
    public void doExtractAuthenticationWithMissingJwt()
            throws ExecutionException, InterruptedException {
        final RequestContext requestContext = mockRequestContext();
        final String correlationId = UUID.randomUUID().toString();

        final DefaultAuthenticationResult authenticationResult =
                underTest.doExtractAuthentication(requestContext, correlationId, messageDispatcher)
                        .get();

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
    public void doExtractAuthenticationWithInvalidJwt()
            throws ExecutionException, InterruptedException {
        when(publicKeyProvider.getPublicKey(ISSUER, KEY_ID)).thenReturn(
                CompletableFuture.completedFuture(Optional.of(PUBLIC_KEY_2)));
        final RequestContext requestContext = mockRequestContext(VALID_AUTHORIZATION_HEADER);
        final String correlationId = UUID.randomUUID().toString();

        final DefaultAuthenticationResult authenticationResult =
                underTest.doExtractAuthentication(requestContext, correlationId, messageDispatcher)
                        .get();

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
        final String correlationId = UUID.randomUUID().toString();
        final DittoRuntimeException dre =
                DittoRuntimeException.newBuilder("none", HttpStatusCode.INTERNAL_SERVER_ERROR).build();
        final IllegalStateException illegalStateException = new IllegalStateException("notExpected", dre);

        final Throwable reasonOfFailure =
                underTest.toFailedAuthenticationResult(illegalStateException, correlationId).getReasonOfFailure();

        assertThat(reasonOfFailure).isEqualTo(dre);
    }

    @Test
    public void toFailedAuthenticationResult() {
        final String correlationId = UUID.randomUUID().toString();
        final DittoRuntimeException dre =
                DittoRuntimeException.newBuilder("none", HttpStatusCode.INTERNAL_SERVER_ERROR).build();

        final Throwable reasonOfFailure =
                underTest.toFailedAuthenticationResult(dre, correlationId).getReasonOfFailure();

        assertThat(reasonOfFailure).isEqualTo(dre);
    }

    @Test
    public void getType() {
        final JwtAuthenticationProvider underTest =
                JwtAuthenticationProvider.getInstance(publicKeyProvider, authenticationContextProvider);

        assertThat(underTest.getType()).isEqualTo("JWT");
    }

    private RequestContext mockRequestContext(final HttpHeader... httpHeaders) {
        final HttpRequest httpRequest = HttpRequest.create().addHeaders(Arrays.asList(httpHeaders));

        final RequestContext requestContext = mock(RequestContext.class);
        when(requestContext.getRequest()).thenReturn(httpRequest);
        return requestContext;
    }
}