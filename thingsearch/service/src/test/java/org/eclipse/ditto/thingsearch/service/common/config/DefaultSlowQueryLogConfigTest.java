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

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link DefaultSlowQueryLogConfig}.
 */
public final class DefaultSlowQueryLogConfigTest {

    private static Config config;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        config = ConfigFactory.load("slow-query-log-test");
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultSlowQueryLogConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final SlowQueryLogConfig underTest = DefaultSlowQueryLogConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.isEnabled())
                .as(SlowQueryLogConfig.SlowQueryLogConfigValue.ENABLED.getConfigPath())
                .isEqualTo(SlowQueryLogConfig.SlowQueryLogConfigValue.ENABLED.getDefaultValue());

        softly.assertThat(underTest.getThreshold())
                .as(SlowQueryLogConfig.SlowQueryLogConfigValue.THRESHOLD.getConfigPath())
                .isEqualTo(SlowQueryLogConfig.SlowQueryLogConfigValue.THRESHOLD.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final SlowQueryLogConfig underTest = DefaultSlowQueryLogConfig.of(config);

        softly.assertThat(underTest.isEnabled())
                .as(SlowQueryLogConfig.SlowQueryLogConfigValue.ENABLED.getConfigPath())
                .isFalse();

        softly.assertThat(underTest.getThreshold())
                .as(SlowQueryLogConfig.SlowQueryLogConfigValue.THRESHOLD.getConfigPath())
                .isEqualTo(Duration.ofMillis(500));
    }

}
