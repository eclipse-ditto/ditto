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
package org.eclipse.ditto.services.connectivity.mapping;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.services.connectivity.mapping.javascript.JavaScriptConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.mapping.DefaultMappingConfig}.
 */
public final class DefaultMappingConfigTest {

    private static Config mappingTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        mappingTestConfig = ConfigFactory.load("mapping-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultMappingConfig.class,
                areImmutable(),
                provided(JavaScriptConfig.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultMappingConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getFactoryNameReturnsDefaultValueIfConfigIsEmpty() {
        final DefaultMappingConfig underTest = DefaultMappingConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getFactoryName()).isEqualTo(MappingConfig.MappingConfigValue.FACTORY.getDefaultValue());
    }

    @Test
    public void getFactoryNameReturnsValueOfConfigurationFile() {
        final DefaultMappingConfig underTest = DefaultMappingConfig.of(mappingTestConfig);

        softly.assertThat(underTest.getFactoryName()).isEqualTo("org.test.mappers.MessageMapperFactory");
    }

    @Test
    public void toStringContainsExpected() {
        final DefaultMappingConfig underTest = DefaultMappingConfig.of(mappingTestConfig);

        softly.assertThat(underTest.toString()).contains(underTest.getClass().getSimpleName())
                .contains("factoryName").contains(underTest.getFactoryName())
                .contains("javaScriptConfig");
    }

}
