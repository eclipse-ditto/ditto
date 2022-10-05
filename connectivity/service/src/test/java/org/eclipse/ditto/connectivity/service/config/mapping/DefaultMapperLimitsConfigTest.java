/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.service.config.mapping;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
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
 * Unit test for {@link DefaultMapperLimitsConfig}.
 */
public class DefaultMapperLimitsConfigTest {

    private static Config mapperLimitsTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        mapperLimitsTestConfig = ConfigFactory.load("mapper-limits-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultMapperLimitsConfig.class,
                areImmutable(),
                provided(MapperLimitsConfig.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultMapperLimitsConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultMapperLimitsConfig underTest = DefaultMapperLimitsConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getMaxSourceMappers())
                .as(MapperLimitsConfig.MapperLimitsConfigValue.MAX_SOURCE_MAPPERS.getConfigPath())
                .isEqualTo(MapperLimitsConfig.MapperLimitsConfigValue.MAX_SOURCE_MAPPERS.getDefaultValue());

        softly.assertThat(underTest.getMaxMappedInboundMessages())
                .as(MapperLimitsConfig.MapperLimitsConfigValue.MAX_MAPPED_INBOUND_MESSAGE.getConfigPath())
                .isEqualTo(MapperLimitsConfig.MapperLimitsConfigValue.MAX_MAPPED_INBOUND_MESSAGE.getDefaultValue());

        softly.assertThat(underTest.getMaxTargetMappers())
                .as(MapperLimitsConfig.MapperLimitsConfigValue.MAX_TARGET_MAPPERS.getConfigPath())
                .isEqualTo(MapperLimitsConfig.MapperLimitsConfigValue.MAX_TARGET_MAPPERS.getDefaultValue());

        softly.assertThat(underTest.getMaxMappedOutboundMessages())
                .as(MapperLimitsConfig.MapperLimitsConfigValue.MAX_MAPPED_OUTBOUND_MESSAGE.getConfigPath())
                .isEqualTo(MapperLimitsConfig.MapperLimitsConfigValue.MAX_MAPPED_OUTBOUND_MESSAGE.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultMapperLimitsConfig underTest = DefaultMapperLimitsConfig.of(mapperLimitsTestConfig);

        softly.assertThat(underTest.getMaxSourceMappers())
                .as(MapperLimitsConfig.MapperLimitsConfigValue.MAX_SOURCE_MAPPERS.getConfigPath())
                .isEqualTo(2);

        softly.assertThat(underTest.getMaxMappedInboundMessages())
                .as(MapperLimitsConfig.MapperLimitsConfigValue.MAX_MAPPED_INBOUND_MESSAGE.getConfigPath())
                .isEqualTo(3);

        softly.assertThat(underTest.getMaxTargetMappers())
                .as(MapperLimitsConfig.MapperLimitsConfigValue.MAX_TARGET_MAPPERS.getConfigPath())
                .isEqualTo(4);

        softly.assertThat(underTest.getMaxMappedOutboundMessages())
                .as(MapperLimitsConfig.MapperLimitsConfigValue.MAX_MAPPED_OUTBOUND_MESSAGE.getConfigPath())
                .isEqualTo(5);
    }
}
