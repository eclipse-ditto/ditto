/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.service.config.limits;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultLimitsConfig}.
 */
public class DefaultLimitsConfigTest {

    private static Config limitsTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        limitsTestConf = ConfigFactory.load("limits-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultLimitsConfig.class,
                areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultLimitsConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultLimitsConfig underTest = DefaultLimitsConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getThingsMaxSize())
                .as(LimitsConfig.LimitsConfigValue.THINGS_MAX_SIZE.getConfigPath())
                .isEqualTo(LimitsConfig.LimitsConfigValue.THINGS_MAX_SIZE.getDefaultValue());

        softly.assertThat(underTest.getPoliciesMaxSize())
                .as(LimitsConfig.LimitsConfigValue.POLICIES_MAX_SIZE.getConfigPath())
                .isEqualTo(LimitsConfig.LimitsConfigValue.POLICIES_MAX_SIZE.getDefaultValue());

        softly.assertThat(underTest.getMessagesMaxSize())
                .as(LimitsConfig.LimitsConfigValue.MESSAGES_MAX_SIZE.getConfigPath())
                .isEqualTo(LimitsConfig.LimitsConfigValue.MESSAGES_MAX_SIZE.getDefaultValue());

        softly.assertThat(underTest.getThingsSearchDefaultPageSize())
                .as(LimitsConfig.LimitsConfigValue.THINGS_SEARCH_DEFAULT_PAGE_SIZE.getConfigPath())
                .isEqualTo(LimitsConfig.LimitsConfigValue.THINGS_SEARCH_DEFAULT_PAGE_SIZE.getDefaultValue());

        softly.assertThat(underTest.getThingsSearchMaxPageSize())
                .as(LimitsConfig.LimitsConfigValue.THINGS_SEARCH_MAX_PAGE_SIZE.getConfigPath())
                .isEqualTo(LimitsConfig.LimitsConfigValue.THINGS_SEARCH_MAX_PAGE_SIZE.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultLimitsConfig underTest = DefaultLimitsConfig.of(limitsTestConf);

        softly.assertThat(underTest.getThingsMaxSize())
                .as(LimitsConfig.LimitsConfigValue.THINGS_MAX_SIZE.getConfigPath())
                .isEqualTo(204800);

        softly.assertThat(underTest.getPoliciesMaxSize())
                .as(LimitsConfig.LimitsConfigValue.POLICIES_MAX_SIZE.getConfigPath())
                .isEqualTo(204800);

        softly.assertThat(underTest.getMessagesMaxSize())
                .as(LimitsConfig.LimitsConfigValue.MESSAGES_MAX_SIZE.getConfigPath())
                .isEqualTo(358400);

        softly.assertThat(underTest.getThingsSearchDefaultPageSize())
                .as(LimitsConfig.LimitsConfigValue.THINGS_SEARCH_DEFAULT_PAGE_SIZE.getConfigPath())
                .isEqualTo(50);

        softly.assertThat(underTest.getThingsSearchMaxPageSize())
                .as(LimitsConfig.LimitsConfigValue.THINGS_SEARCH_MAX_PAGE_SIZE.getConfigPath())
                .isEqualTo(500);
    }
}
