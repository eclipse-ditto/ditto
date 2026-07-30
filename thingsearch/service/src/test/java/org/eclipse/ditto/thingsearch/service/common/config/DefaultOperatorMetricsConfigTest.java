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
package org.eclipse.ditto.thingsearch.service.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.internal.utils.persistence.mongo.config.ReadConcern;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.ReadPreference;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link DefaultOperatorMetricsConfig}, focusing on the optional per-metric-type persistence
 * (read preference / read concern) configuration.
 */
public final class DefaultOperatorMetricsConfigTest {

    @Test
    public void persistenceConfigsAreEmptyWhenNotConfigured() {
        final Config config = ConfigFactory.parseString(
                "operator-metrics {\n" +
                        "  enabled = true\n" +
                        "  scrape-interval = 15m\n" +
                        "  custom-metrics {}\n" +
                        "  custom-aggregation-metrics {}\n" +
                        "}");

        final DefaultOperatorMetricsConfig underTest = DefaultOperatorMetricsConfig.of(config);

        assertThat(underTest.getCustomMetricsPersistenceConfig()).isEmpty();
        assertThat(underTest.getCustomAggregationMetricsPersistenceConfig()).isEmpty();
    }

    @Test
    public void persistenceConfigsAreParsedWhenConfigured() {
        final Config config = ConfigFactory.parseString(
                "operator-metrics {\n" +
                        "  enabled = true\n" +
                        "  scrape-interval = 15m\n" +
                        "  custom-metrics {}\n" +
                        "  custom-aggregation-metrics {}\n" +
                        "  custom-metrics-persistence {\n" +
                        "    readPreference = \"nearest\"\n" +
                        "    readConcern = \"local\"\n" +
                        "  }\n" +
                        "  custom-aggregation-metrics-persistence {\n" +
                        "    readPreference = \"secondaryPreferred\"\n" +
                        "  }\n" +
                        "}");

        final DefaultOperatorMetricsConfig underTest = DefaultOperatorMetricsConfig.of(config);

        assertThat(underTest.getCustomMetricsPersistenceConfig()).hasValueSatisfying(persistenceConfig -> {
            assertThat(persistenceConfig.readPreference()).isEqualTo(ReadPreference.NEAREST);
            assertThat(persistenceConfig.readConcern()).isEqualTo(ReadConcern.LOCAL);
        });
        assertThat(underTest.getCustomAggregationMetricsPersistenceConfig()).hasValueSatisfying(persistenceConfig ->
                // read concern falls back to its own default when not explicitly configured:
                assertThat(persistenceConfig.readPreference()).isEqualTo(ReadPreference.SECONDARY_PREFERRED));
    }

}
