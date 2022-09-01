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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.api.GatewayAuthenticationFailedException;
import org.eclipse.ditto.jwt.model.JwtInvalidException;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link DefaultAuthenticationFailureAggregator}.
 */
public final class DefaultAuthenticationFailureAggregatorTest {

    private static final DittoHeaders KNOWN_DITTO_HEADERS = DittoHeaders.empty();

    private DefaultAuthenticationFailureAggregator underTest;

    @Before
    public void setup() {
        underTest = DefaultAuthenticationFailureAggregator.getInstance();
    }

    @Test
    public void aggregateAuthenticationFailuresWithEmptyList() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.aggregateAuthenticationFailures(Collections.emptyList()));
    }

    @Test
    public void aggregateAuthenticationFailuresWithoutDittoRuntimeExceptions() {
        final List<AuthenticationResult> authenticationResults =
                Collections.singletonList(DefaultAuthenticationResult.failed(KNOWN_DITTO_HEADERS,
                        new IllegalStateException("Not a DRE")));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.aggregateAuthenticationFailures(authenticationResults));
    }

    @Test
    public void aggregateAuthenticationFailuresWitNestedDittoRuntimeExceptionWithoutDescription() {
        final DittoRuntimeException expectedException =
                GatewayAuthenticationFailedException.newBuilder("you're out").description((String) null).build();
        final IllegalStateException reasonOfFailure = new IllegalStateException("Not a DRE", expectedException);
        final List<AuthenticationResult> authenticationResults =
                Collections.singletonList(DefaultAuthenticationResult.failed(KNOWN_DITTO_HEADERS, reasonOfFailure));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.aggregateAuthenticationFailures(authenticationResults));
    }

    @Test
    public void aggregateAuthenticationFailuresWitNestedDittoRuntimeException() {
        final DittoRuntimeException expectedException = GatewayAuthenticationFailedException.newBuilder("you're out")
                .description("foo")
                .build();
        final IllegalStateException reasonOfFailure = new IllegalStateException("Not a DRE", expectedException);
        final List<AuthenticationResult> authenticationResults =
                Collections.singletonList(DefaultAuthenticationResult.failed(KNOWN_DITTO_HEADERS, reasonOfFailure));

        final DittoRuntimeException dittoRuntimeException =
                underTest.aggregateAuthenticationFailures(authenticationResults);

        assertThat(dittoRuntimeException).isEqualTo(expectedException);
    }

    @Test
    public void aggregateAuthenticationFailures() {
        final DittoHeaders fooHeader = DittoHeaders.newBuilder().putHeader("foo-header", "foo").build();
        final DittoRuntimeException exceptionA = GatewayAuthenticationFailedException.newBuilder("you're out")
                .dittoHeaders(fooHeader)
                .description("do this")
                .build();

        final DittoHeaders barHeader = DittoHeaders.newBuilder().putHeader("bar-header", "bar").build();
        final DittoRuntimeException exceptionB = GatewayAuthenticationFailedException.newBuilder("you're out")
                .dittoHeaders(barHeader)
                .description("do that")
                .build();

        final DittoHeaders expectedHeaders = DittoHeaders.newBuilder()
                .putHeaders(fooHeader)
                .putHeaders(barHeader)
                .build();

        final List<AuthenticationResult> authenticationResults =
                Arrays.asList(DefaultAuthenticationResult.failed(KNOWN_DITTO_HEADERS, exceptionA),
                        DefaultAuthenticationResult.failed(KNOWN_DITTO_HEADERS, exceptionB));

        final DittoRuntimeException dittoRuntimeException =
                underTest.aggregateAuthenticationFailures(authenticationResults);

        assertThat(dittoRuntimeException).isExactlyInstanceOf(GatewayAuthenticationFailedException.class)
                .hasMessage("Multiple authentication mechanisms were applicable but none succeeded.");
        assertThat(dittoRuntimeException.getDescription()).contains(
                "For a successful authentication see the following suggestions: { do this }, { do that }.");
        assertThat(dittoRuntimeException.getDittoHeaders()).containsAllEntriesOf(expectedHeaders);
    }

    @Test
    public void aggregateAuthenticationFailuresWithExceptionNotUnauthorized() {
        final DittoHeaders fooHeader = DittoHeaders.newBuilder().putHeader("foo-header", "foo").build();
        final DittoRuntimeException exceptionA = JwtInvalidException.newBuilder()
                .dittoHeaders(fooHeader)
                .description("do this")
                .build();

        final DittoHeaders barHeader = DittoHeaders.newBuilder().putHeader("bar-header", "bar").build();
        final DittoRuntimeException exceptionB = GatewayAuthenticationFailedException.newBuilder("you're out")
                .dittoHeaders(barHeader)
                .description("do that")
                .build();

        final List<AuthenticationResult> authenticationResults =
                Arrays.asList(DefaultAuthenticationResult.failed(KNOWN_DITTO_HEADERS, exceptionA),
                        DefaultAuthenticationResult.failed(KNOWN_DITTO_HEADERS, exceptionB));

        final DittoRuntimeException dittoRuntimeException =
                underTest.aggregateAuthenticationFailures(authenticationResults);

        assertThat(dittoRuntimeException).isEqualTo(exceptionA);
    }

}
