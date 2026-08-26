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
 * (read preference / read concern) configuration and its inheritance from the general {@code query.persistence}
 * configuration.
 */
public final class DefaultOperatorMetricsConfigTest {

    /**
     * Stands in for a deployment which configured non-default read settings for user-facing searches.
     */
    private static final SearchPersistenceConfig QUERY_PERSISTENCE_CONFIG =
            DefaultSearchPersistenceConfig.of(ConfigFactory.parseString(
                    "persistence {\n" +
                            "  readPreference = \"secondaryPreferred\"\n" +
                            "  readConcern = \"majority\"\n" +
                            "}"));

    private static Config operatorMetricsConfig(final String... persistenceBlocks) {
        return ConfigFactory.parseString(
                "operator-metrics {\n" +
                        "  enabled = true\n" +
                        "  scrape-interval = 15m\n" +
                        "  custom-metrics {}\n" +
                        "  custom-aggregation-metrics {}\n" +
                        String.join("\n", persistenceBlocks) + "\n" +
                        "}");
    }

    @Test
    public void persistenceConfigsAreEmptyWhenNotConfigured() {
        final DefaultOperatorMetricsConfig underTest =
                DefaultOperatorMetricsConfig.of(operatorMetricsConfig(), QUERY_PERSISTENCE_CONFIG);

        // an absent block means: use the general query.persistence config, which the callers resolve themselves:
        assertThat(underTest.getCustomMetricsPersistenceConfig()).isEmpty();
        assertThat(underTest.getCustomAggregationMetricsPersistenceConfig()).isEmpty();
    }

    @Test
    public void fullyConfiguredBlockOverridesQueryPersistence() {
        final DefaultOperatorMetricsConfig underTest = DefaultOperatorMetricsConfig.of(operatorMetricsConfig(
                "  custom-metrics-persistence {\n" +
                        "    readPreference = \"nearest\"\n" +
                        "    readConcern = \"local\"\n" +
                        "  }"), QUERY_PERSISTENCE_CONFIG);

        assertThat(underTest.getCustomMetricsPersistenceConfig()).hasValueSatisfying(persistenceConfig -> {
            assertThat(persistenceConfig.readPreference()).isEqualTo(ReadPreference.NEAREST);
            assertThat(persistenceConfig.readConcern()).isEqualTo(ReadConcern.LOCAL);
        });
    }

    @Test
    public void blockConfiguringOnlyReadPreferenceInheritsReadConcernFromQueryPersistence() {
        final DefaultOperatorMetricsConfig underTest = DefaultOperatorMetricsConfig.of(operatorMetricsConfig(
                "  custom-metrics-persistence {\n" +
                        "    readPreference = \"nearest\"\n" +
                        "  }"), QUERY_PERSISTENCE_CONFIG);

        assertThat(underTest.getCustomMetricsPersistenceConfig()).hasValueSatisfying(persistenceConfig -> {
            assertThat(persistenceConfig.readPreference()).isEqualTo(ReadPreference.NEAREST);
            assertThat(persistenceConfig.readConcern()).isEqualTo(ReadConcern.MAJORITY);
        });
    }

    @Test
    public void blockConfiguringOnlyReadConcernInheritsReadPreferenceFromQueryPersistence() {
        final DefaultOperatorMetricsConfig underTest = DefaultOperatorMetricsConfig.of(operatorMetricsConfig(
                "  custom-aggregation-metrics-persistence {\n" +
                        "    readConcern = \"local\"\n" +
                        "  }"), QUERY_PERSISTENCE_CONFIG);

        assertThat(underTest.getCustomAggregationMetricsPersistenceConfig())
                .hasValueSatisfying(persistenceConfig -> {
                    assertThat(persistenceConfig.readConcern()).isEqualTo(ReadConcern.LOCAL);
                    // must not silently fall back to the "primaryPreferred" default of SearchPersistenceConfig:
                    assertThat(persistenceConfig.readPreference()).isEqualTo(ReadPreference.SECONDARY_PREFERRED);
                });
    }

    @Test
    public void bothBlocksAreResolvedIndependently() {
        final DefaultOperatorMetricsConfig underTest = DefaultOperatorMetricsConfig.of(operatorMetricsConfig(
                "  custom-metrics-persistence {\n" +
                        "    readConcern = \"local\"\n" +
                        "  }",
                "  custom-aggregation-metrics-persistence {\n" +
                        "    readPreference = \"secondary\"\n" +
                        "  }"), QUERY_PERSISTENCE_CONFIG);

        assertThat(underTest.getCustomMetricsPersistenceConfig()).hasValueSatisfying(persistenceConfig -> {
            assertThat(persistenceConfig.readPreference()).isEqualTo(ReadPreference.SECONDARY_PREFERRED);
            assertThat(persistenceConfig.readConcern()).isEqualTo(ReadConcern.LOCAL);
        });
        assertThat(underTest.getCustomAggregationMetricsPersistenceConfig())
                .hasValueSatisfying(persistenceConfig -> {
                    assertThat(persistenceConfig.readPreference()).isEqualTo(ReadPreference.SECONDARY);
                    assertThat(persistenceConfig.readConcern()).isEqualTo(ReadConcern.MAJORITY);
                });
    }

    @Test
    public void configuredBlockFallsBackToDefaultsWhenNoQueryPersistenceConfigIsKnown() {
        final DefaultOperatorMetricsConfig underTest = DefaultOperatorMetricsConfig.of(operatorMetricsConfig(
                "  custom-metrics-persistence {\n" +
                        "    readConcern = \"local\"\n" +
                        "  }"));

        assertThat(underTest.getCustomMetricsPersistenceConfig()).hasValueSatisfying(persistenceConfig -> {
            assertThat(persistenceConfig.readPreference()).isEqualTo(ReadPreference.PRIMARY_PREFERRED);
            assertThat(persistenceConfig.readConcern()).isEqualTo(ReadConcern.LOCAL);
        });
    }

}
