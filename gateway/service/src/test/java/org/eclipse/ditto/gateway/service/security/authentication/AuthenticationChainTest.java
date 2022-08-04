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
package org.eclipse.ditto.gateway.service.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.api.GatewayAuthenticationFailedException;
import org.eclipse.ditto.gateway.api.GatewayServiceUnavailableException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import akka.http.javadsl.server.RequestContext;

/**
 * Unit test for {@link AuthenticationChain}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class AuthenticationChainTest {

    private static AuthorizationContext knownAuthorizationContext;
    private static Executor messageDispatcher;

    @Rule public final TestName testName = new TestName();
    @Rule public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Mock private AuthenticationProvider<AuthenticationResult> authenticationProviderA;
    @Mock private AuthenticationProvider<AuthenticationResult> authenticationProviderB;
    @Mock private AuthenticationFailureAggregator authenticationFailureAggregator;

    private DittoHeaders dittoHeaders;

    @BeforeClass
    public static void initTestFixture() {
        knownAuthorizationContext =
                AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance("issuer:subject"));
        messageDispatcher = Executors.newFixedThreadPool(8);
    }

    @Before
    public void setUp() {
        dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(testName.getMethodName())
                .build();
    }

    @Test
    public void getInstanceThrowsIllegalArgumentExceptionWhenProviderListIsEmpty() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(
                        () -> AuthenticationChain.getInstance(Collections.emptyList(), authenticationFailureAggregator,
                                messageDispatcher))
                .withMessage("The argument 'authenticationProviders' must not be empty!")
                .withNoCause();
    }

    @Test
    public void authenticate() throws ExecutionException, InterruptedException {
        final RequestContext requestContextMock = mock(RequestContext.class);
        final AuthenticationResult expectedAuthenticationResult =
                DefaultAuthenticationResult.successful(dittoHeaders, knownAuthorizationContext);
        when(authenticationProviderA.isApplicable(requestContextMock)).thenReturn(false);
        when(authenticationProviderB.isApplicable(requestContextMock)).thenReturn(true);
        when(authenticationProviderB.authenticate(requestContextMock, dittoHeaders))
                .thenReturn(completed(expectedAuthenticationResult));
        final AuthenticationChain underTest =
                AuthenticationChain.getInstance(Arrays.asList(authenticationProviderA, authenticationProviderB),
                        authenticationFailureAggregator,
                        messageDispatcher);

        final AuthenticationResult authenticationResult =
                underTest.authenticate(requestContextMock, dittoHeaders).get();

        verify(authenticationProviderA).isApplicable(requestContextMock);
        verify(authenticationProviderB).isApplicable(requestContextMock);
        verify(authenticationProviderA, never()).authenticate(requestContextMock, dittoHeaders);
        verify(authenticationProviderB).authenticate(requestContextMock, dittoHeaders);
        softly.assertThat(authenticationResult).isEqualTo(expectedAuthenticationResult);
    }

    @Test
    public void authenticateReturnsFirstAuthenticationResultIfMultipleProvidersAreApplicable()
            throws ExecutionException, InterruptedException {

        final RequestContext requestContextMock = mock(RequestContext.class);
        final AuthenticationResult expectedAuthenticationResult =
                DefaultAuthenticationResult.successful(dittoHeaders, knownAuthorizationContext);
        when(authenticationProviderA.isApplicable(requestContextMock)).thenReturn(true);
        when(authenticationProviderA.authenticate(requestContextMock, dittoHeaders))
                .thenReturn(completed(expectedAuthenticationResult));
        final AuthenticationChain underTest =
                AuthenticationChain.getInstance(Arrays.asList(authenticationProviderA, authenticationProviderB),
                        authenticationFailureAggregator, messageDispatcher);

        final AuthenticationResult authenticationResult =
                underTest.authenticate(requestContextMock, dittoHeaders).get();

        verify(authenticationProviderA).isApplicable(requestContextMock);
        verify(authenticationProviderB, never()).isApplicable(requestContextMock);
        verify(authenticationProviderA).authenticate(requestContextMock, dittoHeaders);
        verify(authenticationProviderB, never()).authenticate(requestContextMock, dittoHeaders);
        softly.assertThat(authenticationResult).isEqualTo(expectedAuthenticationResult);
    }

    @Test
    public void authenticateReturnsIllegalStateAuthenticationResultIfCalledWhenNoProvidersAreApplicable()
            throws ExecutionException, InterruptedException {

        final RequestContext requestContextMock = mock(RequestContext.class);
        final IllegalStateException expectedException = new IllegalStateException("No applicable authentication " +
                "provider was found!");
        when(authenticationProviderA.isApplicable(requestContextMock)).thenReturn(false);
        when(authenticationProviderB.isApplicable(requestContextMock)).thenReturn(false);
        final AuthenticationChain underTest =
                AuthenticationChain.getInstance(Arrays.asList(authenticationProviderA, authenticationProviderB),
                        authenticationFailureAggregator,
                        messageDispatcher);

        final AuthenticationResult authenticationResult =
                underTest.authenticate(requestContextMock, dittoHeaders).get();

        verify(authenticationProviderA).isApplicable(requestContextMock);
        verify(authenticationProviderB).isApplicable(requestContextMock);
        verify(authenticationProviderA, never()).authenticate(requestContextMock, dittoHeaders);
        verify(authenticationProviderB, never()).authenticate(requestContextMock, dittoHeaders);
        softly.assertThat(authenticationResult.isSuccess()).isFalse();
        softly.assertThat(authenticationResult.getReasonOfFailure()).isInstanceOf(expectedException.getClass());
        softly.assertThat(authenticationResult.getReasonOfFailure()).hasMessage(expectedException.getMessage());
        softly.assertThat(authenticationResult.getReasonOfFailure()).hasNoCause();
    }

    @Test
    public void authenticateReturnsFailedAuthenticationResult() throws ExecutionException, InterruptedException {
        final RequestContext requestContextMock = mock(RequestContext.class);
        final AuthenticationResult expectedAuthenticationResult =
                DefaultAuthenticationResult.failed(dittoHeaders, mock(DittoRuntimeException.class));
        when(authenticationProviderA.isApplicable(requestContextMock)).thenReturn(true);
        when(authenticationProviderB.isApplicable(requestContextMock)).thenReturn(false);
        when(authenticationProviderA.authenticate(requestContextMock, dittoHeaders))
                .thenReturn(completed(expectedAuthenticationResult));
        final AuthenticationChain underTest =
                AuthenticationChain.getInstance(Arrays.asList(authenticationProviderA, authenticationProviderB),
                        authenticationFailureAggregator,
                        messageDispatcher);

        final AuthenticationResult authenticationResult =
                underTest.authenticate(requestContextMock, dittoHeaders).get();

        verify(authenticationProviderA).isApplicable(requestContextMock);
        verify(authenticationProviderB).isApplicable(requestContextMock);
        verify(authenticationProviderA).authenticate(requestContextMock, dittoHeaders);
        verify(authenticationProviderB, never()).authenticate(requestContextMock, dittoHeaders);
        softly.assertThat(authenticationResult).isEqualTo(expectedAuthenticationResult);
    }

    @Test
    public void synchronousExceptionDoesNotAbortAuthenticationChain() {
        final RequestContext requestContextMock = mock(RequestContext.class);
        final DittoRuntimeException error = GatewayServiceUnavailableException.newBuilder().build();
        final var expectedAuthenticationResult =
                DefaultAuthenticationResult.successful(dittoHeaders, knownAuthorizationContext);
        when(authenticationProviderA.isApplicable(any())).thenReturn(true);
        when(authenticationProviderA.authenticate(any(), any())).thenThrow(error);
        when(authenticationProviderB.isApplicable(any())).thenReturn(true);
        when(authenticationProviderB.authenticate(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(expectedAuthenticationResult));
        final AuthenticationChain underTest =
                AuthenticationChain.getInstance(Arrays.asList(authenticationProviderA, authenticationProviderB),
                        authenticationFailureAggregator,
                        messageDispatcher);

        final AuthenticationResult authenticationResult =
                underTest.authenticate(requestContextMock, dittoHeaders).join();

        assertThat(authenticationResult).isEqualTo(expectedAuthenticationResult);
        verify(authenticationProviderA).isApplicable(eq(requestContextMock));
        verify(authenticationProviderB).isApplicable(eq(requestContextMock));
        verify(authenticationProviderA).authenticate(eq(requestContextMock), eq(dittoHeaders));
        verify(authenticationProviderB).authenticate(eq(requestContextMock), eq(dittoHeaders));
    }

    @Test
    public void asynchronousExceptionDoesNotAbortAuthenticationChain() {
        final RequestContext requestContextMock = mock(RequestContext.class);
        final DittoRuntimeException error = GatewayServiceUnavailableException.newBuilder().build();
        final var expectedAuthenticationResult =
                DefaultAuthenticationResult.successful(dittoHeaders, knownAuthorizationContext);
        when(authenticationProviderA.isApplicable(any())).thenReturn(true);
        when(authenticationProviderA.authenticate(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(error));
        when(authenticationProviderB.isApplicable(any())).thenReturn(true);
        when(authenticationProviderB.authenticate(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(expectedAuthenticationResult));
        final AuthenticationChain underTest =
                AuthenticationChain.getInstance(Arrays.asList(authenticationProviderA, authenticationProviderB),
                        authenticationFailureAggregator,
                        messageDispatcher);

        final AuthenticationResult authenticationResult =
                underTest.authenticate(requestContextMock, dittoHeaders).join();

        assertThat(authenticationResult).isEqualTo(expectedAuthenticationResult);
        verify(authenticationProviderA).isApplicable(eq(requestContextMock));
        verify(authenticationProviderB).isApplicable(eq(requestContextMock));
        verify(authenticationProviderA).authenticate(eq(requestContextMock), eq(dittoHeaders));
        verify(authenticationProviderB).authenticate(eq(requestContextMock), eq(dittoHeaders));
    }

    @Test
    public void authenticateReturnsSuccessfulAuthenticationResultIfOneProviderSucceeds()
            throws ExecutionException, InterruptedException {

        final RequestContext requestContextMock = mock(RequestContext.class);
        final AuthenticationResult failedAuthenticationResult =
                DefaultAuthenticationResult.failed(dittoHeaders, mock(DittoRuntimeException.class));
        final AuthenticationResult expectedAuthenticationResult =
                DefaultAuthenticationResult.successful(dittoHeaders, knownAuthorizationContext);
        when(authenticationProviderA.isApplicable(requestContextMock)).thenReturn(true);
        when(authenticationProviderB.isApplicable(requestContextMock)).thenReturn(true);
        when(authenticationProviderA.authenticate(requestContextMock, dittoHeaders))
                .thenReturn(completed(failedAuthenticationResult));
        when(authenticationProviderB.authenticate(requestContextMock, dittoHeaders))
                .thenReturn(completed(expectedAuthenticationResult));
        final AuthenticationChain underTest =
                AuthenticationChain.getInstance(Arrays.asList(authenticationProviderA, authenticationProviderB),
                        authenticationFailureAggregator,
                        messageDispatcher);

        final AuthenticationResult authenticationResult =
                underTest.authenticate(requestContextMock, dittoHeaders).get();

        verify(authenticationProviderA).isApplicable(requestContextMock);
        verify(authenticationProviderB).isApplicable(requestContextMock);
        verify(authenticationProviderA).authenticate(requestContextMock, dittoHeaders);
        verify(authenticationProviderB).authenticate(requestContextMock, dittoHeaders);
        softly.assertThat(authenticationResult).isEqualTo(expectedAuthenticationResult);
    }

    @Test
    public void authenticateAggregatesFailureDescriptionsIfMultipleProvidersFail()
            throws ExecutionException, InterruptedException {

        final RequestContext requestContextMock = mock(RequestContext.class);

        // Failure A
        final DittoRuntimeException failureA = mock(DittoRuntimeException.class);
        final AuthenticationResult failedAuthenticationResultA =
                DefaultAuthenticationResult.failed(dittoHeaders, failureA);
        when(authenticationProviderA.isApplicable(requestContextMock)).thenReturn(true);
        when(authenticationProviderA.authenticate(requestContextMock, dittoHeaders))
                .thenReturn(completed(failedAuthenticationResultA));
        // Failure B
        final DittoRuntimeException failureB = mock(DittoRuntimeException.class);
        final AuthenticationResult failedAuthenticationResultB =
                DefaultAuthenticationResult.failed(dittoHeaders, failureB);
        when(authenticationProviderB.isApplicable(requestContextMock)).thenReturn(true);

        when(authenticationProviderB.authenticate(requestContextMock, dittoHeaders))
                .thenReturn(completed(failedAuthenticationResultB));
        final AuthenticationChain underTest =
                AuthenticationChain.getInstance(Arrays.asList(authenticationProviderA, authenticationProviderB),
                        authenticationFailureAggregator, messageDispatcher);

        final List<AuthenticationResult> failedAuthenticationResult =
                Arrays.asList(failedAuthenticationResultA, failedAuthenticationResultB);
        when(authenticationFailureAggregator.aggregateAuthenticationFailures(failedAuthenticationResult))
                .thenReturn(GatewayAuthenticationFailedException.newBuilder("you're not authenticated").build());

        final AuthenticationResult authenticationResult =
                underTest.authenticate(requestContextMock, dittoHeaders).get();

        verify(authenticationProviderA).isApplicable(requestContextMock);
        verify(authenticationProviderB).isApplicable(requestContextMock);
        verify(authenticationProviderA).authenticate(requestContextMock, dittoHeaders);
        verify(authenticationProviderB).authenticate(requestContextMock, dittoHeaders);
        verify(authenticationFailureAggregator).aggregateAuthenticationFailures(failedAuthenticationResult);
        softly.assertThat(authenticationResult.isSuccess()).isFalse();
    }

    private static <T> CompletableFuture<T> completed(final T value) {
        return CompletableFuture.completedFuture(value);
    }

}
