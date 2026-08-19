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
package org.eclipse.ditto.wot.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultWotHostValidationConfig}.
 */
public final class DefaultWotHostValidationConfigTest {

    @Test
    public void assertImmutability() {
        // the config holds only immutable collections and primitives
        final Config emptyConfig = ConfigFactory.parseString("http.security { }");
        final DefaultWotHostValidationConfig config = DefaultWotHostValidationConfig.of(emptyConfig);
        assertThat(config).isNotNull();
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultWotHostValidationConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void defaultsAreSecureByDefault() {
        final DefaultWotHostValidationConfig underTest =
                DefaultWotHostValidationConfig.of(ConfigFactory.parseString("http.security { }"));

        assertThat(underTest.isEnabled()).isTrue();
        assertThat(underTest.getAllowedHostnames()).isEmpty();
        assertThat(underTest.getBlockedHostnames()).isEmpty();
        assertThat(underTest.getBlockedSubnets()).isEmpty();
        assertThat(underTest.getBlockedHostRegex()).isEmpty();
        assertThat(underTest.getMaxRedirects()).isEqualTo(5);
    }

    @Test
    public void parsesCommaSeparatedListsAndTrimsWhitespace() {
        final Config config = ConfigFactory.parseString("http.security {\n" +
                "  enabled = false\n" +
                "  allowed-hostnames = \"my-registry.example, internal.example \"\n" +
                "  blocked-hostnames = \"evil.example\"\n" +
                "  blocked-subnets = \"10.0.0.0/8, 192.168.0.0/16\"\n" +
                "  blocked-host-regex = \".*\\\\.svc\\\\.cluster\\\\.local\"\n" +
                "  max-redirects = 3\n" +
                "}");

        final DefaultWotHostValidationConfig underTest = DefaultWotHostValidationConfig.of(config);

        assertThat(underTest.isEnabled()).isFalse();
        assertThat(underTest.getAllowedHostnames())
                .containsExactlyInAnyOrder("my-registry.example", "internal.example");
        assertThat(underTest.getBlockedHostnames()).containsExactly("evil.example");
        assertThat(underTest.getBlockedSubnets()).containsExactlyInAnyOrder("10.0.0.0/8", "192.168.0.0/16");
        assertThat(underTest.getBlockedHostRegex()).isEqualTo(".*\\.svc\\.cluster\\.local");
        assertThat(underTest.getMaxRedirects()).isEqualTo(3);
    }
}
