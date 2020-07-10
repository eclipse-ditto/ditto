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
package org.eclipse.ditto.services.connectivity.messaging.validation;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.validation.HostValidator.HostValidationResult;
import org.junit.Before;
import org.junit.Test;

import akka.event.LoggingAdapter;

/**
 * Tests {@link HostValidator}.
 */
public class HostValidatorTest {

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder().correlationId("ditto").build();
    private ConnectionConfig connectionConfig;
    private ConnectivityConfig connectivityConfig;
    private LoggingAdapter loggingAdapter;

    @Before
    public void setUp() throws Exception {
        connectivityConfig = mock(ConnectivityConfig.class);
        connectionConfig = mock(ConnectionConfig.class);
        when(connectivityConfig.getConnectionConfig()).thenReturn(connectionConfig);
        loggingAdapter = mock(LoggingAdapter.class);

        when(connectionConfig.getBlockedHostnames()).thenReturn(List.of("localhost"));
    }

    @Test
    public void testAllowedBlockedHosts() {

        final HostValidator underTest =
                getHostValidatorWithAllowlist("0.0.0.0", "8.8.8.8", "[::1]", "192.168.0.1", "224.0.1.1");

        // check if allowlist works for fixed (not configured) blocked ips
        assertValid(underTest.validateHost("0.0.0.0"));
        assertValid(underTest.validateHost("8.8.8.8"));
        assertValid(underTest.validateHost("[::1]"));
        assertValid(underTest.validateHost("192.168.0.1"));
        assertValid(underTest.validateHost("224.0.1.1"));
    }

    @Test
    public void expectValidationFailsForInvalidHost() {
        final HostValidator underTest = getHostValidatorWithAllowlist();
        final HostValidationResult validationResult = underTest.validateHost("ditto");
        assertThat(validationResult).extracting(HostValidationResult::isValid).isEqualTo(false);
        final ConnectionConfigurationInvalidException exception = validationResult.toException(DITTO_HEADERS);
        assertThat(exception.getDittoHeaders()).isEqualTo(DITTO_HEADERS);
        assertThat(exception.getMessage()).contains("The configured host 'ditto' is invalid");
    }

    @Test
    public void expectConfiguredAllowedAndBlockedHostIsAllowed() {
        final String eclipseOrg = "eclipse.org";
        final HostValidator underTest =
                getHostValidatorWithCustomResolver(HostValidatorTest::resolveHost, eclipseOrg);
        final HostValidationResult validationResult = underTest.validateHost(eclipseOrg);
        assertThat(validationResult.isValid()).isTrue();
    }

    @Test
    public void expectConfiguredBlockedHostIsBlocked() {
        final HostValidator underTest = getHostValidatorWithCustomResolver(HostValidatorTest::resolveHost);
        final HostValidationResult validationResult = underTest.validateHost("eclipse.org");
        assertThat(validationResult.isValid()).isFalse();
        assertThat(validationResult.toException(DITTO_HEADERS).getDittoHeaders()).isEqualTo(DITTO_HEADERS);
    }

    @Test
    public void expectBlockedHostIsBlocked() {
        // test if a host that resolves to blocked address (hardcoded e.g. loopback, not configured) is blocked
        final String theHost = "eclipse.org";

        // required because empty blocklist disables verification
        when(connectionConfig.getBlockedHostnames()).thenReturn(List.of("dummy.org"));

        // eclipse.org resolves to loopback which is blocked
        final HostValidator.AddressResolver resolveToLoopback =
                host -> resolveHost(theHost, InetAddress.getLoopbackAddress().getAddress());

        final HostValidator underTest = getHostValidatorWithCustomResolver(resolveToLoopback);

        final HostValidationResult validationResult = underTest.validateHost(theHost);
        assertThat(validationResult.isValid()).isFalse();
    }

    private static InetAddress[] resolveHost(final String host, byte... address) throws UnknownHostException {
        return new InetAddress[]{
                InetAddress.getByAddress(host, address.length != 4 ? new byte[]{1, 2, 3, 4} : address)
        };
    }

    private HostValidator getHostValidatorWithAllowlist(final String... allowlist) {
        when(connectionConfig.getAllowedHostnames()).thenReturn(List.of(allowlist));
        return new HostValidator(connectivityConfig, loggingAdapter);
    }

    private HostValidator getHostValidatorWithCustomResolver(final HostValidator.AddressResolver resolver) {
        when(connectionConfig.getAllowedHostnames()).thenReturn(emptyList());
        return new HostValidator(connectivityConfig, loggingAdapter, resolver);
    }

    private HostValidator getHostValidatorWithCustomResolver(final HostValidator.AddressResolver resolver,
            final String... allowlist) {
        when(connectionConfig.getAllowedHostnames()).thenReturn(List.of(allowlist));
        return new HostValidator(connectivityConfig, loggingAdapter, resolver);
    }

    private void assertValid(final HostValidationResult result) {
        assertThat(result.isValid()).isTrue();
    }
}