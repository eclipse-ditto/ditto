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

import static org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtTestConstants.VALID_JWT_TOKEN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import org.assertj.core.api.AssertionsForClassTypes;
import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.common.BinaryValidationResult;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.jwt.ImmutableJsonWebToken;
import org.eclipse.ditto.model.jwt.JsonWebToken;
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationResult;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
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

    @Rule public final TestName testName = new TestName();
    @Rule public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Mock private JwtAuthenticationResultProvider authenticationContextProvider;
    @Mock private JwtValidator jwtValidator;

    private DittoHeaders knownDittoHeaders;
    private JwtAuthenticationProvider underTest;

    @Before
    public void setup() {
        knownDittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
        underTest = JwtAuthenticationProvider.newInstance(authenticationContextProvider, jwtValidator);
    }

    @Test
    public void isApplicable() {
        final RequestContext requestContext = mockRequestContext(VALID_AUTHORIZATION_HEADER);

        softly.assertThat(underTest.isApplicable(requestContext)).isTrue();
    }

    @Test
    public void isApplicableWithoutAuthorizationHeader() {
        final RequestContext requestContext = mockRequestContext();

        softly.assertThat(underTest.isApplicable(requestContext)).isFalse();
    }

    @Test
    public void isApplicableWithInvalidAuthorizationHeader() {
        final RequestContext requestContext = mockRequestContext(INVALID_AUTHORIZATION_HEADER);

        softly.assertThat(underTest.isApplicable(requestContext)).isFalse();
    }

    @Test
    public void doExtractAuthentication() {
        when(jwtValidator.validate(any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(BinaryValidationResult.valid()));
        when(authenticationContextProvider.getAuthenticationResult(any(JsonWebToken.class), any(DittoHeaders.class)))
                .thenReturn(JwtAuthenticationResult.successful(knownDittoHeaders,
                        AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                                AuthorizationSubject.newInstance("myAuthSubj")),
                        ImmutableJsonWebToken.fromToken(VALID_JWT_TOKEN)));
        final RequestContext requestContext = mockRequestContext(VALID_AUTHORIZATION_HEADER);

        final AuthenticationResult authenticationResult =
                underTest.authenticate(requestContext, knownDittoHeaders).join();

        softly.assertThat(authenticationResult.isSuccess()).isTrue();
    }

    @Test
    public void doExtractAuthenticationWhenAuthorizationContextProviderErrors() {
        when(jwtValidator.validate(any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(BinaryValidationResult.valid()));
        when(authenticationContextProvider.getAuthenticationResult(any(JsonWebToken.class), any(DittoHeaders.class)))
                .thenThrow(new RuntimeException("Something happened"));
        final RequestContext requestContext = mockRequestContext(VALID_AUTHORIZATION_HEADER);

        final AuthenticationResult authenticationResult =
                underTest.authenticate(requestContext, knownDittoHeaders).join();

        verify(authenticationContextProvider).getAuthenticationResult(any(JsonWebToken.class), any(DittoHeaders.class));
        softly.assertThat(authenticationResult.isSuccess()).isFalse();
        softly.assertThat(authenticationResult.getReasonOfFailure())
                .isExactlyInstanceOf(GatewayAuthenticationFailedException.class);

        final GatewayAuthenticationFailedException reasonOfFailure =
                (GatewayAuthenticationFailedException) authenticationResult.getReasonOfFailure();

        softly.assertThat(reasonOfFailure).hasMessage("The JWT could not be verified.");
        softly.assertThat(reasonOfFailure.getDescription()).contains("Something happened");
        softly.assertThat(reasonOfFailure.getDittoHeaders().getCorrelationId())
                .isEqualTo(knownDittoHeaders.getCorrelationId());
    }

    @Test
    public void doExtractAuthenticationWithMissingJwt() {
        final RequestContext requestContext = mockRequestContext();

        final AuthenticationResult authenticationResult =
                underTest.authenticate(requestContext, knownDittoHeaders).join();

        softly.assertThat(authenticationResult.isSuccess()).isFalse();
        softly.assertThat(authenticationResult.getReasonOfFailure())
                .isInstanceOf(GatewayAuthenticationFailedException.class);
        final DittoRuntimeException reasonOfFailureDre =
                (DittoRuntimeException) authenticationResult.getReasonOfFailure();
        softly.assertThat(reasonOfFailureDre).hasMessage("The JWT was missing.");
        softly.assertThat(reasonOfFailureDre.getErrorCode()).isEqualTo("gateway:authentication.failed");
        softly.assertThat(reasonOfFailureDre.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        softly.assertThat(reasonOfFailureDre.getDittoHeaders().getCorrelationId())
                .isEqualTo(knownDittoHeaders.getCorrelationId());
        softly.assertThat(reasonOfFailureDre.getDescription())
                .contains("Please provide a valid JWT in the authorization header prefixed with 'Bearer '");
    }

    @Test
    public void doExtractAuthenticationWithInvalidJwt() {
        when(jwtValidator.validate(any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        BinaryValidationResult.invalid(new IllegalStateException("foo"))));
        final RequestContext requestContext = mockRequestContext(VALID_AUTHORIZATION_HEADER);

        final AuthenticationResult authenticationResult =
                underTest.authenticate(requestContext, knownDittoHeaders).join();

        softly.assertThat(authenticationResult.isSuccess()).isFalse();
        softly.assertThat(authenticationResult.getReasonOfFailure())
                .isInstanceOf(GatewayAuthenticationFailedException.class);
        final DittoRuntimeException reasonOfFailureDre =
                (DittoRuntimeException) authenticationResult.getReasonOfFailure();
        softly.assertThat(reasonOfFailureDre).hasMessage("The JWT could not be verified.");
        softly.assertThat(reasonOfFailureDre.getErrorCode()).isEqualTo("gateway:authentication.failed");
        softly.assertThat(reasonOfFailureDre.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        softly.assertThat(reasonOfFailureDre.getDittoHeaders().getCorrelationId())
                .isEqualTo(knownDittoHeaders.getCorrelationId());
    }

    @Test
    public void toFailedAuthenticationResultExtractsDittoRuntimeExceptionFromCause() {
        final DittoRuntimeException dre = PublicKeyProviderUnavailableException.newBuilder().build();
        final IllegalStateException illegalStateException = new IllegalStateException("notExpected", dre);

        final Throwable reasonOfFailure =
                underTest.toFailedAuthenticationResult(illegalStateException, knownDittoHeaders).getReasonOfFailure();

        softly.assertThat(reasonOfFailure).isEqualTo(dre);
    }

    @Test
    public void toFailedAuthenticationResult() {
        final DittoRuntimeException dre = PublicKeyProviderUnavailableException.newBuilder().build();

        final AuthenticationResult authenticationResult =
                underTest.toFailedAuthenticationResult(dre, knownDittoHeaders);

        softly.assertThat(authenticationResult.getReasonOfFailure()).isEqualTo(dre);
    }

    @Test
    public void getType() {
        AssertionsForClassTypes.assertThat(underTest.getType(mockRequestContext(VALID_AUTHORIZATION_HEADER)))
                .isEqualTo(DittoAuthorizationContextType.JWT);
    }

    private static RequestContext mockRequestContext(final HttpHeader... httpHeaders) {
        final HttpRequest httpRequest = HttpRequest.create().addHeaders(Arrays.asList(httpHeaders));

        final RequestContext requestContext = mock(RequestContext.class);
        when(requestContext.getRequest()).thenReturn(httpRequest);
        return requestContext;
    }

}
