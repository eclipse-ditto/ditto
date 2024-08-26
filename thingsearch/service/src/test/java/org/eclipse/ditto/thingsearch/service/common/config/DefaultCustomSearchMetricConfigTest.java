/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 */

package org.eclipse.ditto.thingsearch.service.common.config;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

public class DefaultCustomSearchMetricConfigTest {

    private static Config config;
    private static Config customSearchMetricTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        config = ConfigFactory.load("custom-search-metric-test");
        customSearchMetricTestConfig = config.getConfig("ditto.search.operator-metrics.custom-search-metrics");
    }

    @Test
    public void assertImmutabilityFilterConfig() {
        assertInstancesOf(DefaultCustomSearchMetricConfig.DefaultFilterConfig.class, areImmutable(), provided(Config.class).isAlsoImmutable());
    }
    @Test
    public void assertImmutabilityCustomSearchMetricConfig() {
        assertInstancesOf(DefaultCustomSearchMetricConfig.class, areImmutable(), provided(Config.class).isAlsoImmutable(),
                provided(DefaultCustomSearchMetricConfig.FilterConfig.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultCustomSearchMetricConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void gettersReturnConfiguredValues() {
        final DefaultCustomSearchMetricConfig underTest =
                DefaultCustomSearchMetricConfig.of("online_status",
                        customSearchMetricTestConfig.getConfig("online_status"));

        softly.assertThat(underTest.isEnabled())
                .as(CustomSearchMetricConfig.CustomSearchMetricConfigValue.ENABLED.getConfigPath())
                .isEqualTo(true);
        softly.assertThat(underTest.getScrapeInterval())
                .as(CustomSearchMetricConfig.CustomSearchMetricConfigValue.SCRAPE_INTERVAL.getConfigPath())
                .isEqualTo(Optional.ofNullable(customSearchMetricTestConfig.getDuration(
                        "online_status.scrape-interval")));
        softly.assertThat(underTest.getNamespaces())
                .as(CustomSearchMetricConfig.CustomSearchMetricConfigValue.NAMESPACES.getConfigPath())
                .containsExactlyInAnyOrder("org.eclipse.ditto.test.1", "org.eclipse.ditto.test.2");
        softly.assertThat(underTest.getTags())
                .as(CustomSearchMetricConfig.CustomSearchMetricConfigValue.TAGS.getConfigPath())
                .containsExactlyInAnyOrderEntriesOf(
                        customSearchMetricTestConfig.getObject("online_status.tags")
                                .unwrapped().entrySet().stream().collect(
                                        Collectors.toMap(Map.Entry::getKey, o -> o.getValue().toString())));
        softly.assertThat(underTest.getFilterConfigs())
                .as(CustomSearchMetricConfig.CustomSearchMetricConfigValue.FILTERS.getConfigPath())
                .hasSize(2);
        softly.assertThat(underTest.getFilterConfigs().get(0).getFilterName())
                .as("filter name")
                .isEqualTo("online_filter");
        softly.assertThat(underTest.getFilterConfigs().get(1).getFilterName())
                .as("filter name")
                .isEqualTo("offline_filter");
        softly.assertThat(underTest.getTags())
                .as("tags")
                .containsExactlyInAnyOrderEntriesOf(
                        customSearchMetricTestConfig.getObject("online_status.tags")
                                .unwrapped().entrySet().stream().collect(
                                        Collectors.toMap(Map.Entry::getKey, o -> o.getValue().toString())));
    }
}