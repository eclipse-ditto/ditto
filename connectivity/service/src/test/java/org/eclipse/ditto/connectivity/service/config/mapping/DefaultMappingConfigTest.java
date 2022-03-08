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
import org.eclipse.ditto.connectivity.service.config.javascript.JavaScriptConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultMappingConfig}.
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
    public void toStringContainsExpected() {
        final DefaultMappingConfig underTest = DefaultMappingConfig.of(mappingTestConfig);

        softly.assertThat(underTest.toString())
                .contains(underTest.getClass().getSimpleName())
                .contains("javaScriptConfig", "mapperLimitsConfig", "publishFailedEnrichments", "bufferSize", "parallelism", "maxPoolSize");
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultMappingConfig underTest = DefaultMappingConfig.of(mappingTestConfig);

        softly.assertThat(underTest.getBufferSize())
                .describedAs(MappingConfig.MappingConfigValue.BUFFER_SIZE.getConfigPath())
                .isEqualTo(12345);

        softly.assertThat(underTest.getParallelism())
                .describedAs(MappingConfig.MappingConfigValue.PARALLELISM.getConfigPath())
                .isEqualTo(67890);

        softly.assertThat(underTest.getMaxPoolSize())
                .describedAs(MappingConfig.MappingConfigValue.MAX_POOL_SIZE.getConfigPath())
                .isEqualTo(37);

        softly.assertThat(underTest.getPublishFailedEnrichments())
                .describedAs(MappingConfig.MappingConfigValue.PUBLISH_FAILED_ENRICHMENTS.getConfigPath())
                .isEqualTo(true);
    }

}
