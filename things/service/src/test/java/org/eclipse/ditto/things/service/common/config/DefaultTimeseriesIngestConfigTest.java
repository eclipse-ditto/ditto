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
package org.eclipse.ditto.things.service.common.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

public final class DefaultTimeseriesIngestConfigTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void defaultsApplyWithoutConfig() {
        final TimeseriesIngestConfig underTest = DefaultTimeseriesIngestConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getMaxMeasurementAge()).isEqualTo(Duration.ofDays(35));
        softly.assertThat(underTest.getRetryMaxAttempts()).isEqualTo(10);
        softly.assertThat(underTest.getRetryBackoff()).isEqualTo(Duration.ofMillis(500));
        softly.assertThat(underTest.getRetryMaxBackoff()).isEqualTo(Duration.ofSeconds(30));
        softly.assertThat(underTest.getRetryMaxPendingRetries()).isEqualTo(20_000);
    }

    @Test
    public void readsConfiguredValuesIncludingEnvironmentStrings() {
        final TimeseriesIngestConfig underTest = DefaultTimeseriesIngestConfig.of(ConfigFactory.parseString(
                "timeseries { max-measurement-age = 7d, ingest-retry { max-attempts = \"4\", backoff = \"1s\", " +
                        "max-backoff = 5s, max-pending-retries = \"7\" } }"));

        softly.assertThat(underTest.getMaxMeasurementAge()).isEqualTo(Duration.ofDays(7));
        softly.assertThat(underTest.getRetryMaxAttempts()).isEqualTo(4);
        softly.assertThat(underTest.getRetryBackoff()).isEqualTo(Duration.ofSeconds(1));
        softly.assertThat(underTest.getRetryMaxBackoff()).isEqualTo(Duration.ofSeconds(5));
        softly.assertThat(underTest.getRetryMaxPendingRetries()).isEqualTo(7);
    }

    @Test
    public void rejectsInvalidValues() {
        for (final String invalid : List.of("max-measurement-age = 0s", "ingest-retry.max-attempts = 0",
                "ingest-retry.max-pending-retries = -1", "ingest-retry.backoff = 0s",
                "ingest-retry { backoff = 10s, max-backoff = 1s }", "ingest-retry.max-backoff = 2h",
                "ingest-retry.max-attempts = many")) {
            assertThatThrownBy(() -> DefaultTimeseriesIngestConfig.of(
                    ConfigFactory.parseString("timeseries { " + invalid + " }")))
                    .as(invalid)
                    .isInstanceOf(DittoConfigError.class);
        }
    }
}
