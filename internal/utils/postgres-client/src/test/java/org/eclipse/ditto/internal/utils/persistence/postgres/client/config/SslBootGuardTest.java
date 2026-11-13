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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit test for the SSL refuse-to-boot guard.
 */
public final class SslBootGuardTest {

    private static DefaultSslConfig sslConfigFrom(final String hocon) {
        final Config config = ConfigFactory.parseString(hocon);
        return DefaultSslConfig.of(config);
    }

    @Test
    public void verifyFullWithoutRootCertAndNoSystemTruststoreRefusesToBoot() {
        final DefaultSslConfig underTest = sslConfigFrom(
                "ssl { mode = verify-full, root-cert = \"\", allow-system-truststore = false }");

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(underTest::validateBootGuard)
                .withMessageContaining("Refusing to boot")
                .withMessageContaining("verify-full")
                .withMessageContaining("root-cert");
    }

    @Test
    public void verifyCaWithoutRootCertAndNoSystemTruststoreRefusesToBoot() {
        final DefaultSslConfig underTest = sslConfigFrom(
                "ssl { mode = verify-ca, root-cert = \"\", allow-system-truststore = false }");

        assertThatExceptionOfType(DittoConfigError.class).isThrownBy(underTest::validateBootGuard);
    }

    @Test
    public void verifyFullWithRootCertBoots() {
        final DefaultSslConfig underTest = sslConfigFrom(
                "ssl { mode = verify-full, root-cert = \"/etc/ditto/ca.crt\", allow-system-truststore = false }");

        assertThatCode(underTest::validateBootGuard).doesNotThrowAnyException();
    }

    @Test
    public void verifyFullWithoutRootCertButSystemTruststoreAllowedBoots() {
        final DefaultSslConfig underTest = sslConfigFrom(
                "ssl { mode = verify-full, root-cert = \"\", allow-system-truststore = true }");

        assertThatCode(underTest::validateBootGuard).doesNotThrowAnyException();
    }

    @Test
    public void requireModeWithoutRootCertBoots() {
        // 'require' encrypts but does not verify the chain -> no trust anchor needed -> no guard trip.
        final DefaultSslConfig underTest = sslConfigFrom(
                "ssl { mode = require, root-cert = \"\", allow-system-truststore = false }");

        assertThatCode(underTest::validateBootGuard).doesNotThrowAnyException();
    }

    @Test
    public void disableModeBoots() {
        final DefaultSslConfig underTest = sslConfigFrom("ssl { mode = disable }");

        assertThatCode(underTest::validateBootGuard).doesNotThrowAnyException();
    }

}
