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
package org.eclipse.ditto.services.gateway.security.authentication.dummy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.gateway.security.authentication.DefaultAuthenticationResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.Query;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.RequestContext;
import akka.japi.Pair;

/**
 * Tests {@link DummyAuthenticationProvider}.
 */
@RunWith(MockitoJUnitRunner.class)
public class DummyAuthenticationProviderTest {

    private static final String DUMMY_AUTH_HEADER_NAME =
            org.eclipse.ditto.services.gateway.security.HttpHeader.X_DITTO_DUMMY_AUTH.getName();
    private static final HttpHeader DUMMY_AUTH_HEADER = HttpHeader.parse(DUMMY_AUTH_HEADER_NAME, "myDummy");
    private static final Query DUMMY_AUTH_QUERY = Query.create(new Pair<>(DUMMY_AUTH_HEADER_NAME, "myDummy"));

    private DummyAuthenticationProvider underTest;

    @Before
    public void setup() {
        underTest = DummyAuthenticationProvider.getInstance();
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
        final String correlationId = UUID.randomUUID().toString();
        final RequestContext requestContext = mockRequestContext(null, null);

        final DefaultAuthenticationResult authenticationResult =
                underTest.doExtractAuthentication(requestContext, correlationId);

        assertThat(authenticationResult.isSuccess()).isFalse();
    }

    @Test
    public void doExtractAuthenticationFailsWithEmptyDummyAuthHeader() {
        final String correlationId = UUID.randomUUID().toString();
        final RequestContext requestContext = mockRequestContext(HttpHeader.parse(DUMMY_AUTH_HEADER_NAME, ""), null);

        final DefaultAuthenticationResult authenticationResult =
                underTest.doExtractAuthentication(requestContext, correlationId);

        assertThat(authenticationResult.isSuccess()).isFalse();
    }

    @Test
    public void doExtractAuthenticationFailsWithEmptyDummyAuthQueryParam() {
        final String correlationId = UUID.randomUUID().toString();
        final RequestContext requestContext =
                mockRequestContext(null, Query.create(Pair.create(DUMMY_AUTH_HEADER_NAME, "")));

        final DefaultAuthenticationResult authenticationResult =
                underTest.doExtractAuthentication(requestContext, correlationId);

        assertThat(authenticationResult.isSuccess()).isFalse();
    }

    @Test
    public void doExtractAuthenticationByHeader() {
        final String correlationId = UUID.randomUUID().toString();
        final RequestContext requestContext = mockRequestContext(DUMMY_AUTH_HEADER, null);

        final DefaultAuthenticationResult authenticationResult =
                underTest.doExtractAuthentication(requestContext, correlationId);

        assertThat(authenticationResult.isSuccess()).isTrue();
    }

    @Test
    public void doExtractAuthenticationByQueryParam() {
        final String correlationId = UUID.randomUUID().toString();
        final RequestContext requestContext = mockRequestContext(null, DUMMY_AUTH_QUERY);

        final DefaultAuthenticationResult authenticationResult =
                underTest.doExtractAuthentication(requestContext, correlationId);

        assertThat(authenticationResult.isSuccess()).isTrue();
    }

    @Test
    public void doExtractAuthenticationByQueryParamAndHeader() {
        final String correlationId = UUID.randomUUID().toString();
        final RequestContext requestContext = mockRequestContext(DUMMY_AUTH_HEADER, DUMMY_AUTH_QUERY);

        final DefaultAuthenticationResult authenticationResult =
                underTest.doExtractAuthentication(requestContext, correlationId);

        assertThat(authenticationResult.isSuccess()).isTrue();
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
        final String type = underTest.getType();

        assertThat(type).isEqualTo("dummy");
    }

    private RequestContext mockRequestContext(@Nullable final HttpHeader httpHeader, @Nullable final Query query) {
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