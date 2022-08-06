/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.api.GatewayAuthenticationFailedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultAuthenticationResult}.
 */
public final class DefaultAuthenticationResultTest {

    @Rule
    public final TestName testName = new TestName();

    private AuthorizationContext authorizationContext;
    private DittoHeaders dittoHeaders;
    private Throwable reasonOfFailure;

    @Before
    public void setUp() {
        authorizationContext = AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                AuthorizationSubject.newInstance("test:myAuthSubject"),
                AuthorizationSubject.newInstance("myAuthSubject"));

        dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
        reasonOfFailure = GatewayAuthenticationFailedException.newBuilder("This is a test message.")
                .dittoHeaders(dittoHeaders)
                .build();
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultAuthenticationResult.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getSuccessfulInstanceWithNullDittoHeaders() {
        assertThatNullPointerException()
                .isThrownBy(() -> DefaultAuthenticationResult.successful(null, authorizationContext))
                .withMessage("The dittoHeaders must not be null!")
                .withNoCause();
    }

    @Test
    public void getSuccessfulInstanceWithNullAuthorizationContext() {
        assertThatNullPointerException()
                .isThrownBy(() -> DefaultAuthenticationResult.successful(dittoHeaders, null))
                .withMessage("The authorizationContext must not be null!")
                .withNoCause();
    }

    @Test
    public void getFailedInstanceWithNullDittoHeaders() {
        assertThatNullPointerException()
                .isThrownBy(() -> DefaultAuthenticationResult.failed(null, reasonOfFailure))
                .withMessage("The dittoHeaders must not be null!")
                .withNoCause();
    }

    @Test
    public void getFailedInstanceWithNullReasonOfFailure() {
        assertThatNullPointerException()
                .isThrownBy(() -> DefaultAuthenticationResult.failed(dittoHeaders, null))
                .withMessage("The reasonOfFailure must not be null!")
                .withNoCause();
    }

    @Test
    public void successfulInstanceIsSuccess() {
        final AuthenticationResult underTest =
                DefaultAuthenticationResult.successful(dittoHeaders, authorizationContext);

        assertThat(underTest.isSuccess()).isTrue();
    }

    @Test
    public void failedInstanceIsNotSuccess() {
        final AuthenticationResult underTest =
                DefaultAuthenticationResult.failed(dittoHeaders, reasonOfFailure);

        assertThat(underTest.isSuccess()).isFalse();
    }

    @Test
    public void getDittoHeadersFromSuccessfulInstance() {
        final DittoHeaders expected = DittoHeaders.newBuilder(dittoHeaders)
                .authorizationContext(authorizationContext)
                .build();

        final AuthenticationResult underTest =
                DefaultAuthenticationResult.successful(dittoHeaders, authorizationContext);

        assertThat(underTest.getDittoHeaders()).isEqualTo(expected);
    }

    @Test
    public void getDittoHeadersFromFailedInstance() {
        final AuthenticationResult underTest = DefaultAuthenticationResult.failed(dittoHeaders, reasonOfFailure);

        assertThat(underTest.getDittoHeaders()).isEqualTo(dittoHeaders);
    }

    @Test
    public void getAuthorizationContextFromSuccessfulInstance() {
        final AuthenticationResult underTest =
                DefaultAuthenticationResult.successful(dittoHeaders, authorizationContext);

        assertThat(underTest.getAuthorizationContext()).isEqualTo(authorizationContext);
    }

    @Test
    public void getAuthorizationContextFromFailedInstanceOfRuntimeException() {
        final AuthenticationResult underTest = DefaultAuthenticationResult.failed(dittoHeaders, reasonOfFailure);

        assertThatThrownBy(underTest::getAuthorizationContext).isEqualTo(reasonOfFailure);
    }

    @Test
    public void getAuthorizationContextFromFailedInstanceOfError() {
        final AssertionError assertionError = new AssertionError("This is a test.");
        final AuthenticationResult underTest = DefaultAuthenticationResult.failed(dittoHeaders, assertionError);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(underTest::getAuthorizationContext)
                .withCause(assertionError);
    }

    @Test
    public void getReasonOfFailureFromFailedInstance() {
        final AuthenticationResult underTest = DefaultAuthenticationResult.failed(dittoHeaders, reasonOfFailure);

        assertThat(underTest.getReasonOfFailure()).isEqualTo(reasonOfFailure);
    }

    @Test
    public void getReasonOfFailureFromSuccessfulInstance() {
        final AuthenticationResult underTest =
                DefaultAuthenticationResult.successful(dittoHeaders, authorizationContext);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(underTest::getReasonOfFailure)
                .withMessage("Authentication was successful!")
                .withNoCause();
    }

}
