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
package org.eclipse.ditto.timeseries.iotdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link DefaultIoTDbTimeseriesAdapterConfig}.
 */
public final class DefaultIoTDbTimeseriesAdapterConfigTest {

    @Test
    public void hashCodeAndEqualsContract() {
        EqualsVerifier.forClass(DefaultIoTDbTimeseriesAdapterConfig.class).usingGetClass().verify();
    }

    @Test
    public void emptyConfigYieldsDefaults() {
        final DefaultIoTDbTimeseriesAdapterConfig underTest =
                DefaultIoTDbTimeseriesAdapterConfig.of(ConfigFactory.empty());

        assertThat(underTest.getHost()).isEqualTo("localhost");
        assertThat(underTest.getPort()).isEqualTo(6667);
        assertThat(underTest.getUsername()).isEqualTo("root");
        assertThat(underTest.getPassword()).isEqualTo("root");
        assertThat(underTest.getDatabase()).isEqualTo("ditto_ts");
        assertThat(underTest.getTablePrefix()).isEqualTo("ts_");
        assertThat(underTest.getMaxSessions()).isEqualTo(16);
        assertThat(underTest.getRetention()).isEmpty();
        assertThat(underTest.getRetentionOverrides()).isEmpty();
        assertThat(underTest.getMaxQueryResultSize()).isEqualTo(1_000_000);
        assertThat(underTest.getQueryTimeout()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    public void valuesAreRead() {
        final DefaultIoTDbTimeseriesAdapterConfig underTest = DefaultIoTDbTimeseriesAdapterConfig.of(
                ConfigFactory.parseString("host = iotdb\nport = 6668\nusername = ditto\npassword = secret\n" +
                        "database = ts\ntable-prefix = \"\"\nmax-sessions = 4\nretention = 30d\n" +
                        "retention-overrides { \"org.eclipse.ditto\" = 365d }\nmax-query-result-size = 10\n" +
                        "query-timeout = 5s"));

        assertThat(underTest.getHost()).isEqualTo("iotdb");
        assertThat(underTest.getPort()).isEqualTo(6668);
        assertThat(underTest.getUsername()).isEqualTo("ditto");
        assertThat(underTest.getPassword()).isEqualTo("secret");
        assertThat(underTest.getDatabase()).isEqualTo("ts");
        assertThat(underTest.getTablePrefix()).isEmpty();
        assertThat(underTest.getMaxSessions()).isEqualTo(4);
        assertThat(underTest.getRetention("other")).contains(Duration.ofDays(30));
        assertThat(underTest.getRetention("org.eclipse.ditto")).contains(Duration.ofDays(365));
        assertThat(underTest.getMaxQueryResultSize()).isEqualTo(10);
        assertThat(underTest.getQueryTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    public void unlimitedRetentionDisablesExpiration() {
        assertThat(DefaultIoTDbTimeseriesAdapterConfig.of(ConfigFactory.parseString("retention = unlimited"))
                .getRetention()).isEmpty();
    }

    @Test
    public void passwordIsNotPrinted() {
        assertThat(DefaultIoTDbTimeseriesAdapterConfig.of(ConfigFactory.parseString("password = s3cr3t"))
                .toString()).doesNotContain("s3cr3t");
    }

    @Test
    public void invalidValuesFailFast() {
        for (final String invalid : new String[]{"database = \"ditto-ts\"", "database = \"Ditto\"",
                "table-prefix = \"ts;\"", "port = 0", "max-sessions = 0", "max-query-result-size = 0",
                "query-timeout = 0s", "retention = 0s", "retention-overrides { \"a.b\" = unlimited }"}) {
            assertThatExceptionOfType(DittoConfigError.class).as(invalid)
                    .isThrownBy(() -> DefaultIoTDbTimeseriesAdapterConfig.of(ConfigFactory.parseString(invalid)));
        }
    }
}
