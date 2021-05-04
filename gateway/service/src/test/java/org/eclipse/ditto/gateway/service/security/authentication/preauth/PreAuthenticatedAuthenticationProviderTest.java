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
package org.eclipse.ditto.gateway.service.security.authentication.preauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.annotation.Nullable;

import org.assertj.core.api.AssertionsForClassTypes;
import org.eclipse.ditto.base.model.auth.AuthorizationContextType;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationResult;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.PublicKeyProviderUnavailableException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.Query;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.RequestContext;
import akka.japi.Pair;

/**
 * Unit test {@link PreAuthenticatedAuthenticationProvider}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class PreAuthenticatedAuthenticationProviderTest {

    private static final String DUMMY_AUTH_HEADER_NAME =
            org.eclipse.ditto.gateway.service.security.HttpHeader.X_DITTO_PRE_AUTH.getName();
    private static final HttpHeader DUMMY_AUTH_HEADER = HttpHeader.parse(DUMMY_AUTH_HEADER_NAME, "myDummy");
    private static final Query DUMMY_AUTH_QUERY = Query.create(new Pair<>(DUMMY_AUTH_HEADER_NAME, "myDummy"));

    @Rule
    public final TestName testName = new TestName();

    private DittoHeaders knownDittoHeaders;
    private PreAuthenticatedAuthenticationProvider underTest;

    @Before
    public void setup() {
        knownDittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
        underTest = PreAuthenticatedAuthenticationProvider.getInstance();
    }

    @Test
    public void isApplicableWithDummyAuthHeader() {
        final boolean applicable = underTest.isApplicable(mockRequestContext(DUMMY_AUTH_HEADER, null));

        assertThat(applicable).isTrue();
    }

    @Test
    public void isApplicableWithDummyAuthQueryParam() {
        final boolean applicable = underTest.isApplicable(mockRequestContext(null, DUMMY_AUTH_QUERY));

        assertThat(applicable).isTrue();
    }

    @Test
    public void isApplicableWithDummyAuthQueryParamAndDummyAuthHeader() {
        final boolean applicable = underTest.isApplicable(mockRequestContext(DUMMY_AUTH_HEADER, DUMMY_AUTH_QUERY));

        assertThat(applicable).isTrue();
    }

    @Test
    public void isApplicableFails() {
        final boolean applicable = underTest.isApplicable(mockRequestContext(null, null));

        assertThat(applicable).isFalse();
    }

    @Test
    public void doExtractAuthenticationFails() {
        final RequestContext requestContext = mockRequestContext(null, null);

        final AuthenticationResult authenticationResult =
                underTest.tryToAuthenticate(requestContext, knownDittoHeaders).join();

        assertThat(authenticationResult.isSuccess()).isFalse();
    }

    @Test
    public void doExtractAuthenticationFailsWithEmptyDummyAuthHeader() {
        final RequestContext requestContext = mockRequestContext(HttpHeader.parse(DUMMY_AUTH_HEADER_NAME, ""), null);

        final AuthenticationResult authenticationResult =
                underTest.tryToAuthenticate(requestContext, knownDittoHeaders).join();

        assertThat(authenticationResult.isSuccess()).isFalse();
    }

    @Test
    public void doExtractAuthenticationFailsWithEmptyDummyAuthQueryParam() {
        final RequestContext requestContext =
                mockRequestContext(null, Query.create(Pair.create(DUMMY_AUTH_HEADER_NAME, "")));

        final AuthenticationResult authenticationResult =
                underTest.tryToAuthenticate(requestContext, knownDittoHeaders).join();

        assertThat(authenticationResult.isSuccess()).isFalse();
    }

    @Test
    public void doExtractAuthenticationByHeader() {
        final RequestContext requestContext = mockRequestContext(DUMMY_AUTH_HEADER, null);

        final AuthenticationResult authenticationResult =
                underTest.tryToAuthenticate(requestContext, knownDittoHeaders).join();

        assertThat(authenticationResult.isSuccess()).isTrue();
    }

    @Test
    public void doExtractAuthenticationByQueryParam() {
        final RequestContext requestContext = mockRequestContext(null, DUMMY_AUTH_QUERY);

        final AuthenticationResult authenticationResult =
                underTest.tryToAuthenticate(requestContext, knownDittoHeaders).join();

        assertThat(authenticationResult.isSuccess()).isTrue();
    }

    @Test
    public void doExtractAuthenticationByQueryParamAndHeader() {
        final RequestContext requestContext = mockRequestContext(DUMMY_AUTH_HEADER, DUMMY_AUTH_QUERY);

        final AuthenticationResult authenticationResult =
                underTest.tryToAuthenticate(requestContext, knownDittoHeaders).join();

        assertThat(authenticationResult.isSuccess()).isTrue();
    }

    @Test
    public void toFailedAuthenticationResultExtractsDittoRuntimeExceptionFromCause() {
        final DittoRuntimeException dre = PublicKeyProviderUnavailableException.newBuilder().build();
        final IllegalStateException illegalStateException = new IllegalStateException("notExpected", dre);

        final Throwable reasonOfFailure =
                underTest.toFailedAuthenticationResult(illegalStateException, knownDittoHeaders).getReasonOfFailure();

        assertThat(reasonOfFailure).isEqualTo(dre);
    }

    @Test
    public void toFailedAuthenticationResult() {
        final DittoRuntimeException dre = PublicKeyProviderUnavailableException.newBuilder().build();

        final Throwable reasonOfFailure =
                underTest.toFailedAuthenticationResult(dre, knownDittoHeaders).getReasonOfFailure();

        assertThat(reasonOfFailure).isEqualTo(dre);
    }

    @Test
    public void getType() {
        final AuthorizationContextType type =
                underTest.getType(mockRequestContext(DUMMY_AUTH_HEADER, DUMMY_AUTH_QUERY));

        AssertionsForClassTypes.assertThat(type).isEqualTo(DittoAuthorizationContextType.PRE_AUTHENTICATED_HTTP);
    }

    private static RequestContext mockRequestContext(@Nullable final HttpHeader httpHeader,
            @Nullable final Query query) {

        Uri uri = Uri.create("someUri");

        if (query != null) {
            uri = uri.query(query);
        }

        HttpRequest httpRequest = HttpRequest.create().withUri(uri);

        if (httpHeader != null) {
            httpRequest = httpRequest.addHeader(httpHeader);
        }

        final RequestContext requestContext = mock(RequestContext.class);
        when(requestContext.getRequest()).thenReturn(httpRequest);

        return requestContext;
    }

}
