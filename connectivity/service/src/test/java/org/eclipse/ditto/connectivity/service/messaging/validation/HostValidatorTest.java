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
package org.eclipse.ditto.connectivity.service.messaging.validation;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.service.config.ConnectionConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.junit.Before;
import org.junit.Test;

import akka.event.LoggingAdapter;

/**
 * Tests {@link DefaultHostValidator}.
 */
public class HostValidatorTest {

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder().correlationId("ditto").build();
    private ConnectionConfig connectionConfig;
    private ConnectivityConfig connectivityConfig;
    private LoggingAdapter loggingAdapter;

    @Before
    public void setUp() {
        connectivityConfig = mock(ConnectivityConfig.class);
        connectionConfig = mock(ConnectionConfig.class);
        when(connectivityConfig.getConnectionConfig()).thenReturn(connectionConfig);
        loggingAdapter = mock(LoggingAdapter.class);

        when(connectionConfig.getBlockedHostnames()).thenReturn(List.of("localhost"));
        when(connectionConfig.getBlockedSubnets()).thenReturn(List.of("11.1.0.0/16", "169.254.0.0/16"));
        when(connectionConfig.getBlockedHostRegex()).thenReturn("");
    }

    @Test
    public void testAllowedBlockedHosts() {

        final HostValidator underTest =
                getHostValidatorWithAllowlist("0.0.0.0", "8.8.8.8", "[::1]", "192.168.0.1", "224.0.1.1");

        // check if allow-list works for fixed (not configured) blocked ips
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

        assertTrue(validationResult.isValid());
    }

    @Test
    public void expectConfiguredBlockedHostIsBlocked() {
        final HostValidator underTest = getHostValidatorWithCustomResolver(HostValidatorTest::resolveHost);
        final HostValidationResult validationResult = underTest.validateHost("eclipse.org");

        assertFalse(validationResult.isValid());
        assertThat(validationResult.toException(DITTO_HEADERS).getDittoHeaders()).isEqualTo(DITTO_HEADERS);
    }

    @Test
    public void expectBlockedHostIsBlocked() {
        // test if a host that resolves to blocked address (hardcoded e.g. loopback, not configured) is blocked
        final String theHost = "eclipse.org";

        // required because empty block-list disables verification
        when(connectionConfig.getBlockedHostnames()).thenReturn(List.of("dummy.org"));

        // eclipse.org resolves to loopback which is blocked
        final DefaultHostValidator.AddressResolver resolveToLoopback =
                host -> resolveHost(theHost, InetAddress.getLoopbackAddress().getAddress());

        final HostValidator underTest = getHostValidatorWithCustomResolver(resolveToLoopback);

        final HostValidationResult validationResult = underTest.validateHost(theHost);

        assertFalse(validationResult.isValid());
    }

    @Test
    public void expectIpsInBlockedSubnetsAreBlocked() {
        final HostValidator underTest = getHostValidatorWithAllowlist();

        assertFalse(underTest.validateHost("11.1.0.1").isValid());
        assertFalse(underTest.validateHost("11.1.255.254").isValid());
        assertFalse(underTest.validateHost("169.254.0.1").isValid());
        assertFalse(underTest.validateHost("169.254.255.254").isValid());
    }

    @Test
    public void expectKubernetesClusterDNSIsBlocked() {
        final HostValidator underTest = getHostValidatorWithBlockedRegexPattern();

        assertFalse(underTest.validateHost("gateway.things.svc.cluster.local").isValid());
    }

    private static InetAddress[] resolveHost(final String host, byte... address) throws UnknownHostException {
        return new InetAddress[]{
                InetAddress.getByAddress(host, address.length != 4 ? new byte[]{1, 2, 3, 4} : address)
        };
    }

    private HostValidator getHostValidatorWithAllowlist(final String... allowlist) {
        when(connectionConfig.getAllowedHostnames()).thenReturn(List.of(allowlist));
        return new DefaultHostValidator(connectivityConfig, loggingAdapter);
    }

    private HostValidator getHostValidatorWithCustomResolver(final DefaultHostValidator.AddressResolver resolver) {
        when(connectionConfig.getAllowedHostnames()).thenReturn(emptyList());
        return new DefaultHostValidator(connectivityConfig, loggingAdapter, resolver);
    }

    private HostValidator getHostValidatorWithCustomResolver(final DefaultHostValidator.AddressResolver resolver,
            final String... allowlist) {
        when(connectionConfig.getAllowedHostnames()).thenReturn(List.of(allowlist));
        return new DefaultHostValidator(connectivityConfig, loggingAdapter, resolver);
    }

    private HostValidator getHostValidatorWithBlockedRegexPattern() {
        when(connectionConfig.getBlockedHostRegex()).thenReturn("^.*\\.svc.cluster.local$");
        return new DefaultHostValidator(connectivityConfig, loggingAdapter);
    }

    private void assertValid(final HostValidationResult result) {
        assertTrue(result.isValid());
    }

}
