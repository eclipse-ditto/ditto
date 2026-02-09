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

import java.util.Optional;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public final class DefaultCustomMetricConfigTest {

    private static Config customMetricTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        final Config config = ConfigFactory.load("custom-search-metric-test");
        customMetricTestConfig = config.getConfig("ditto.search.operator-metrics.custom-metrics");
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultCustomMetricConfig.class)
                .usingGetClass()
                .suppress(Warning.ALL_FIELDS_SHOULD_BE_USED) // customMetricName is intentionally excluded
                .verify();
    }

    @Test
    public void configWithStringIndexHint() {
        final DefaultCustomMetricConfig underTest =
                DefaultCustomMetricConfig.of("count_with_hint",
                        customMetricTestConfig.getConfig("count_with_hint"));

        softly.assertThat(underTest.isEnabled())
                .as("enabled")
                .isTrue();
        softly.assertThat(underTest.getIndexHint())
                .as("indexHint")
                .contains(JsonValue.of("idx_my_count_hint"));
    }

    @Test
    public void configWithObjectIndexHint() {
        final DefaultCustomMetricConfig underTest =
                DefaultCustomMetricConfig.of("count_with_object_hint",
                        customMetricTestConfig.getConfig("count_with_object_hint"));

        softly.assertThat(underTest.isEnabled())
                .as("enabled")
                .isTrue();
        softly.assertThat(underTest.getIndexHint())
                .as("indexHint")
                .isPresent();
        softly.assertThat(underTest.getIndexHint().get().isObject())
                .as("indexHint is object")
                .isTrue();
        softly.assertThat(underTest.getIndexHint().get().asObject())
                .as("indexHint object content")
                .isEqualTo(JsonObject.newBuilder().set("t.attributes.region", 1).build());
    }

    @Test
    public void configWithoutIndexHint() {
        final DefaultCustomMetricConfig underTest =
                DefaultCustomMetricConfig.of("count_without_hint",
                        customMetricTestConfig.getConfig("count_without_hint"));

        softly.assertThat(underTest.isEnabled())
                .as("enabled")
                .isTrue();
        softly.assertThat(underTest.getIndexHint())
                .as("indexHint")
                .isEqualTo(Optional.empty());
    }
}
