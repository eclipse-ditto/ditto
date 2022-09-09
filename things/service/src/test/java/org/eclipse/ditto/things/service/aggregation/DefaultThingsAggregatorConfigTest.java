/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.aggregation;

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
 * Unit test for {@link DefaultThingsAggregatorConfig}.
 */
public final class DefaultThingsAggregatorConfigTest {

    private static Config thingsAggregatorTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        thingsAggregatorTestConf = ConfigFactory.load("things-aggregator-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultThingsAggregatorConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultThingsAggregatorConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultThingsAggregatorConfig underTest = DefaultThingsAggregatorConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getSingleRetrieveThingTimeout())
                .as(ThingsAggregatorConfig.ThingsAggregatorConfigValue.SINGLE_RETRIEVE_THING_TIMEOUT.getConfigPath())
                .isEqualTo(ThingsAggregatorConfig.ThingsAggregatorConfigValue.SINGLE_RETRIEVE_THING_TIMEOUT.getDefaultValue());

        softly.assertThat(underTest.getMaxParallelism())
                .as(ThingsAggregatorConfig.ThingsAggregatorConfigValue.MAX_PARALLELISM.getConfigPath())
                .isEqualTo(ThingsAggregatorConfig.ThingsAggregatorConfigValue.MAX_PARALLELISM.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultThingsAggregatorConfig underTest = DefaultThingsAggregatorConfig.of(thingsAggregatorTestConf);

        softly.assertThat(underTest.getSingleRetrieveThingTimeout())
                .as(ThingsAggregatorConfig.ThingsAggregatorConfigValue.SINGLE_RETRIEVE_THING_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(60L));

        softly.assertThat(underTest.getMaxParallelism())
                .as(ThingsAggregatorConfig.ThingsAggregatorConfigValue.MAX_PARALLELISM.getConfigPath())
                .isEqualTo(10);
    }

}
