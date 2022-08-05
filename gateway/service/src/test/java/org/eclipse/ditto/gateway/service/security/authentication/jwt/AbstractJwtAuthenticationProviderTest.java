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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import org.assertj.core.api.AssertionsForClassTypes;
import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.common.BinaryValidationResult;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.api.GatewayAuthenticationFailedException;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationProvider;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationResult;
import org.eclipse.ditto.jwt.model.ImmutableJsonWebToken;
import org.eclipse.ditto.jwt.model.JsonWebToken;
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
 * Provides tests for {@link JwtAuthenticationProvider}.
 */
@RunWith(MockitoJUnitRunner.class)
abstract class AbstractJwtAuthenticationProviderTest {

    private static final HttpHeader VALID_AUTHORIZATION_HEADER =
            HttpHeader.parse("authorization", "Bearer " + JwtTestConstants.VALID_JWT_TOKEN);
    private static final HttpHeader INVALID_AUTHORIZATION_HEADER =
            HttpHeader.parse("authorization", "Basic " + JwtTestConstants.VALID_JWT_TOKEN);
    protected static final String
            URI_WITH_ACCESS_TOKEN_PARAMETER =
            "https://localhost/ws/2?x=5&access_token=" + JwtTestConstants.VALID_JWT_TOKEN;

    @Rule public final TestName testName = new TestName();
    @Rule public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Mock protected JwtAuthenticationResultProvider authenticationContextProvider;
    @Mock protected JwtValidator jwtValidator;

    protected DittoHeaders knownDittoHeaders;

    /**
     * @return the JwtAuthenticationProvider instance under test
     */
    protected abstract JwtAuthenticationProvider getUnderTest();

    /**
     * @return whether the instance under test supports the access token parameter
     */
    protected abstract boolean supportsAccessTokenParameter();

    /**
     * @return the expected error description
     */
    protected abstract String getExpectedMissingJwtDescription();

    @Test
    public void isApplicableWithHeader() {
        final RequestContext requestContext = mockRequestContext(VALID_AUTHORIZATION_HEADER);
        softly.assertThat(getUnderTest().isApplicable(requestContext)).isTrue();
    }

    @Test
    public void isApplicableWithAccessTokenParameter() {
        final RequestContext requestContext = mockRequestContext(URI_WITH_ACCESS_TOKEN_PARAMETER);
        softly.assertThat(getUnderTest().isApplicable(requestContext)).isEqualTo(supportsAccessTokenParameter());
    }

    @Test
    public void isApplicableWithAccessTokenParameterAndHeader() {
        final RequestContext requestContext = mockRequestContext(URI_WITH_ACCESS_TOKEN_PARAMETER,
                VALID_AUTHORIZATION_HEADER);
        softly.assertThat(getUnderTest().isApplicable(requestContext)).isTrue();
    }

    @Test
    public void isApplicableWithoutAuthorizationHeaderOrAccessTokenParameter() {
        final RequestContext requestContext = mockRequestContext();
        softly.assertThat(getUnderTest().isApplicable(requestContext)).isFalse();
    }

    @Test
    public void isApplicableWithInvalidAuthorizationHeader() {
        final RequestContext requestContext = mockRequestContext(INVALID_AUTHORIZATION_HEADER);
        softly.assertThat(getUnderTest().isApplicable(requestContext)).isFalse();
    }

    @Test
    public void doExtractAuthenticationFromRequestHeaders() {
        when(jwtValidator.validate(any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(BinaryValidationResult.valid()));
        when(authenticationContextProvider.getAuthenticationResult(any(JsonWebToken.class), any(DittoHeaders.class)))
                .thenReturn(CompletableFuture.completedStage(JwtAuthenticationResult.successful(knownDittoHeaders,
                        AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                                AuthorizationSubject.newInstance("myAuthSubj")),
                        ImmutableJsonWebToken.fromToken(JwtTestConstants.VALID_JWT_TOKEN))));
        final RequestContext requestContext = mockRequestContext(VALID_AUTHORIZATION_HEADER);

        authenticate(getUnderTest(), requestContext, true);
    }

    @Test
    public void doExtractAuthenticationFromAccessTokenParameter() {
        lenient().when(jwtValidator.validate(any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(BinaryValidationResult.valid()));
        lenient().when(
                authenticationContextProvider.getAuthenticationResult(any(JsonWebToken.class), any(DittoHeaders.class)))
                .thenReturn(CompletableFuture.completedStage(JwtAuthenticationResult.successful(knownDittoHeaders,
                        AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                                AuthorizationSubject.newInstance("myAuthSubj")),
                        ImmutableJsonWebToken.fromToken(JwtTestConstants.VALID_JWT_TOKEN))));
        final RequestContext requestContext =
                mockRequestContext(URI_WITH_ACCESS_TOKEN_PARAMETER);

        authenticate(getUnderTest(), requestContext, supportsAccessTokenParameter());
    }

    @Test
    public void doExtractAuthenticationWhenAuthorizationContextProviderErrors() {
        when(jwtValidator.validate(any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(BinaryValidationResult.valid()));
        when(authenticationContextProvider.getAuthenticationResult(any(JsonWebToken.class), any(DittoHeaders.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Something happened")));
        final RequestContext requestContext = mockRequestContext(VALID_AUTHORIZATION_HEADER);

        final AuthenticationResult authenticationResult =
                getUnderTest().authenticate(requestContext, knownDittoHeaders).join();

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
                getUnderTest().authenticate(requestContext, knownDittoHeaders).join();

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
                .contains(getExpectedMissingJwtDescription());
    }

    @Test
    public void doExtractAuthenticationWithInvalidJwtFromHeader() {
        final RequestContext requestContext = mockRequestContext(VALID_AUTHORIZATION_HEADER);
        doExtractAuthenticationWithInvalidJwt(getUnderTest(), requestContext);
    }

    protected void doExtractAuthenticationWithInvalidJwt(final AuthenticationProvider<AuthenticationResult> provider,
            final RequestContext requestContext) {
        when(jwtValidator.validate(any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        BinaryValidationResult.invalid(new IllegalStateException("foo"))));

        final AuthenticationResult authenticationResult =
                provider.authenticate(requestContext, knownDittoHeaders).join();

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
                getUnderTest().toFailedAuthenticationResult(illegalStateException, knownDittoHeaders)
                        .getReasonOfFailure();

        softly.assertThat(reasonOfFailure).isEqualTo(dre);
    }

    @Test
    public void toFailedAuthenticationResult() {
        final DittoRuntimeException dre = PublicKeyProviderUnavailableException.newBuilder().build();
        final AuthenticationResult authenticationResult =
                getUnderTest().toFailedAuthenticationResult(dre, knownDittoHeaders);

        softly.assertThat(authenticationResult.getReasonOfFailure()).isEqualTo(dre);
    }

    @Test
    public void getTypeWithAuthorizationHeader() {
        AssertionsForClassTypes.assertThat(getUnderTest().getType(mockRequestContext(VALID_AUTHORIZATION_HEADER)))
                .isEqualTo(DittoAuthorizationContextType.JWT);
    }

    @Test
    public void getTypeWithAccessTokenParameter() {
        AssertionsForClassTypes.assertThat(getUnderTest().getType(mockRequestContext(URI_WITH_ACCESS_TOKEN_PARAMETER)))
                .isEqualTo(DittoAuthorizationContextType.JWT);
    }

    private static RequestContext mockRequestContext(final HttpHeader... httpHeaders) {
        final HttpRequest httpRequest = HttpRequest.create().addHeaders(Arrays.asList(httpHeaders));

        final RequestContext requestContext = mock(RequestContext.class);
        when(requestContext.getRequest()).thenReturn(httpRequest);
        return requestContext;
    }

    protected static RequestContext mockRequestContext(final String uri) {
        final HttpRequest httpRequest = HttpRequest.create(uri);
        final RequestContext requestContext = mock(RequestContext.class);
        when(requestContext.getRequest()).thenReturn(httpRequest);
        return requestContext;
    }

    private static RequestContext mockRequestContext(final String uri, final HttpHeader... httpHeaders) {
        final HttpRequest httpRequest = HttpRequest.create(uri).addHeaders(Arrays.asList(httpHeaders));
        final RequestContext requestContext = mock(RequestContext.class);
        when(requestContext.getRequest()).thenReturn(httpRequest);
        return requestContext;
    }

    private void authenticate(final AuthenticationProvider<AuthenticationResult> provider,
            final RequestContext requestContext,
            final boolean expectedResult) {
        final AuthenticationResult authenticationResult =
                provider.authenticate(requestContext, knownDittoHeaders).join();
        softly.assertThat(authenticationResult.isSuccess()).isEqualTo(expectedResult);
    }

}
