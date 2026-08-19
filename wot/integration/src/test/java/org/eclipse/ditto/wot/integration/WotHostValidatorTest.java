/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import org.apache.pekko.event.LoggingAdapter;
import org.eclipse.ditto.wot.api.config.DefaultWotHostValidationConfig;
import org.eclipse.ditto.wot.api.config.WotHostValidationConfig;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link WotHostValidator}, exercising the SSRF host-validation logic without touching the network.
 */
public final class WotHostValidatorTest {

    private static final LoggingAdapter LOGGING_ADAPTER = mock(LoggingAdapter.class);

    private static WotHostValidationConfig config(final String securityBody) {
        return DefaultWotHostValidationConfig.of(
                ConfigFactory.parseString("http.security {\n" + securityBody + "\n}"));
    }

    private static WotHostValidator.AddressResolver resolverFor(final Map<String, String> hostToIp) {
        return host -> {
            final String ip = hostToIp.getOrDefault(host, host);
            return new InetAddress[]{InetAddress.getByName(ip)};
        };
    }

    private static WotHostValidator validator(final String securityBody, final Map<String, String> hostToIp) {
        return new WotHostValidator(config(securityBody), LOGGING_ADAPTER, resolverFor(hostToIp));
    }

    @Test
    public void blocksLoopbackAddress() {
        final WotHostValidator underTest = validator("", Map.of("evil.example", "127.0.0.1"));
        assertThat(underTest.validateHost("evil.example").isValid()).isFalse();
        assertThat(underTest.validateHost("127.0.0.1").isValid()).isFalse();
    }

    @Test
    public void blocksLinkLocalCloudMetadataAddress() {
        // 169.254.169.254 is the cloud instance-metadata endpoint and is a *link-local* address -
        // Connectivity's DefaultHostValidator would NOT block it, this validator must.
        final WotHostValidator underTest = validator("", Map.of("metadata", "169.254.169.254"));
        assertThat(underTest.validateHost("metadata").isValid()).isFalse();
        assertThat(underTest.validateHost("169.254.169.254").isValid()).isFalse();
    }

    @Test
    public void blocksSiteLocalAddresses() {
        final WotHostValidator underTest = validator("", Map.of());
        assertThat(underTest.validateHost("10.0.0.5").isValid()).isFalse();
        assertThat(underTest.validateHost("192.168.1.1").isValid()).isFalse();
        assertThat(underTest.validateHost("172.16.0.1").isValid()).isFalse();
    }

    @Test
    public void blocksMulticastAndWildcardAddresses() {
        final WotHostValidator underTest = validator("", Map.of());
        assertThat(underTest.validateHost("224.0.0.1").isValid()).isFalse();
        assertThat(underTest.validateHost("0.0.0.0").isValid()).isFalse();
    }

    @Test
    public void allowsPublicAddress() {
        final WotHostValidator underTest = validator("", Map.of("registry.example", "93.184.216.34"));
        assertThat(underTest.validateHost("registry.example").isValid()).isTrue();
    }

    @Test
    public void allowListOverridesBlockedAddress() {
        final WotHostValidator underTest =
                validator("allowed-hostnames = \"localhost\"", Map.of("localhost", "127.0.0.1"));
        assertThat(underTest.validateHost("localhost").isValid()).isTrue();
    }

    @Test
    public void disabledValidationAllowsEverything() {
        final WotHostValidator underTest = validator("enabled = false", Map.of("evil.example", "127.0.0.1"));
        assertThat(underTest.validateHost("evil.example").isValid()).isTrue();
        assertThat(underTest.validateHost("169.254.169.254").isValid()).isTrue();
    }

    @Test
    public void blockedHostRegexBlocksMatchingHost() {
        final WotHostValidator underTest = validator("blocked-host-regex = \".*\\\\.svc\\\\.cluster\\\\.local\"",
                Map.of("kubernetes.default.svc.cluster.local", "93.184.216.34",
                        "registry.example", "93.184.216.34"));
        assertThat(underTest.validateHost("kubernetes.default.svc.cluster.local").isValid()).isFalse();
        // a non-matching public host is still allowed:
        assertThat(underTest.validateHost("registry.example")
                .isValid()).isTrue();
    }

    @Test
    public void blockedSubnetBlocksContainedAddress() {
        final WotHostValidator underTest =
                validator("blocked-subnets = \"100.64.0.0/10\"", Map.of("carrier", "100.64.1.2"));
        assertThat(underTest.validateHost("carrier").isValid()).isFalse();
    }

    @Test
    public void blockedHostnameResolvingToPublicAddressIsBlocked() {
        final WotHostValidator underTest = validator("blocked-hostnames = \"blocked-registry.example\"",
                Map.of("blocked-registry.example", "93.184.216.34", "alias.example", "93.184.216.34"));
        // both hostnames resolve to the same address which is blocked via blocked-hostnames:
        assertThat(underTest.validateHost("alias.example").isValid()).isFalse();
    }

    @Test
    public void unresolvableHostIsInvalid() {
        final WotHostValidator underTest = new WotHostValidator(config(""), LOGGING_ADAPTER, host -> {
            throw new UnknownHostException(host);
        });
        assertThat(underTest.validateHost("does-not-resolve.invalid").isValid()).isFalse();
    }
}
