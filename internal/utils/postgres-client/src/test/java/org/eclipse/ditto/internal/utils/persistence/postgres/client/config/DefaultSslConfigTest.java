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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link DefaultSslConfig} focusing on {@code ssl.mode} validation at HOCON-load time.
 */
public final class DefaultSslConfigTest {

    private static Config sslConfig(final String mode) {
        return ConfigFactory.parseString("ssl.mode = \"" + mode + "\"");
    }

    @Test
    public void rejectsMisspelledModeWithDittoConfigError() {
        // "verify_full" is a typo for "verify-full" — it must be rejected at load time with a DittoConfigError,
        // not leak the raw r2dbc IllegalArgumentException at pool-build time.
        assertThatThrownBy(() -> DefaultSslConfig.of(sslConfig("verify_full")))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("ssl.mode")
                .hasMessageContaining("verify_full");
    }

    @Test
    public void rejectsCompletelyUnknownMode() {
        assertThatThrownBy(() -> DefaultSslConfig.of(sslConfig("bogus")))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("ssl.mode");
    }

    @Test
    public void acceptsAllValidModes() {
        for (final String mode : new String[] {
                "disable", "allow", "prefer", "require", "verify-ca", "verify-full", "tunnel"}) {
            final DefaultSslConfig underTest = DefaultSslConfig.of(sslConfig(mode));
            assertThat(underTest.getMode()).as("mode %s", mode).isEqualTo(mode);
        }
    }

    @Test
    public void normalisesValidModeCasingAndWhitespace() {
        final DefaultSslConfig underTest = DefaultSslConfig.of(sslConfig("  VERIFY-FULL  "));
        assertThat(underTest.getMode()).isEqualTo("verify-full");
    }
}
