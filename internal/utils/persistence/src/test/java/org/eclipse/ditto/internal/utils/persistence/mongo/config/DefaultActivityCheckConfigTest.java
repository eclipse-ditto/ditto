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
package org.eclipse.ditto.internal.utils.persistence.mongo.config;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultActivityCheckConfig}.
 */
public final class DefaultActivityCheckConfigTest {

    private static Config activityCheckTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();


    @BeforeClass
    public static void initTestFixture() {
        activityCheckTestConf = ConfigFactory.load("activity-check-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultActivityCheckConfig.class,
                areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultActivityCheckConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultActivityCheckConfig underTest = DefaultActivityCheckConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getInactiveInterval())
                .as(ActivityCheckConfig.ActivityCheckConfigValue.INACTIVE_INTERVAL.getConfigPath())
                .isEqualTo(ActivityCheckConfig.ActivityCheckConfigValue.INACTIVE_INTERVAL.getDefaultValue());
        softly.assertThat(underTest.getDeletedInterval())
                .as(ActivityCheckConfig.ActivityCheckConfigValue.DELETED_INTERVAL.getConfigPath())
                .isEqualTo(ActivityCheckConfig.ActivityCheckConfigValue.DELETED_INTERVAL.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultActivityCheckConfig underTest = DefaultActivityCheckConfig.of(activityCheckTestConf);

        softly.assertThat(underTest.getInactiveInterval())
                .as(ActivityCheckConfig.ActivityCheckConfigValue.INACTIVE_INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofDays(1L));
        softly.assertThat(underTest.getDeletedInterval())
                .as(ActivityCheckConfig.ActivityCheckConfigValue.DELETED_INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofDays(100L));
    }
}
