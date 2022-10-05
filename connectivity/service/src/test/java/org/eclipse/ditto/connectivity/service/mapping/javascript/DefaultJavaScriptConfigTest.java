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
package org.eclipse.ditto.connectivity.service.mapping.javascript;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.nio.file.Path;
import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.connectivity.service.config.javascript.DefaultJavaScriptConfig;
import org.eclipse.ditto.connectivity.service.config.javascript.JavaScriptConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.connectivity.service.config.javascript.DefaultJavaScriptConfig}.
 */
public final class DefaultJavaScriptConfigTest {

    private static Config javascriptTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        javascriptTestConf = ConfigFactory.load("javascript-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultJavaScriptConfig.class,
                areImmutable(),
                provided(JavaScriptConfig.class, Path.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultJavaScriptConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultJavaScriptConfig underTest = DefaultJavaScriptConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getMaxScriptSizeBytes())
                .as(JavaScriptConfig.JavaScriptConfigValue.MAX_SCRIPT_SIZE_BYTES.getConfigPath())
                .isEqualTo(JavaScriptConfig.JavaScriptConfigValue.MAX_SCRIPT_SIZE_BYTES.getDefaultValue());

        softly.assertThat(underTest.getMaxScriptExecutionTime())
                .as(JavaScriptConfig.JavaScriptConfigValue.MAX_SCRIPT_EXECUTION_TIME.getConfigPath())
                .isEqualTo(JavaScriptConfig.JavaScriptConfigValue.MAX_SCRIPT_EXECUTION_TIME.getDefaultValue());

        softly.assertThat(underTest.getMaxScriptStackDepth())
                .as(JavaScriptConfig.JavaScriptConfigValue.MAX_SCRIPT_STACK_DEPTH.getConfigPath())
                .isEqualTo(JavaScriptConfig.JavaScriptConfigValue.MAX_SCRIPT_STACK_DEPTH.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultJavaScriptConfig underTest = DefaultJavaScriptConfig.of(javascriptTestConf);

        softly.assertThat(underTest.getMaxScriptSizeBytes())
                .as(JavaScriptConfig.JavaScriptConfigValue.MAX_SCRIPT_SIZE_BYTES.getConfigPath())
                .isEqualTo(10000);

        softly.assertThat(underTest.getMaxScriptExecutionTime())
                .as(JavaScriptConfig.JavaScriptConfigValue.MAX_SCRIPT_EXECUTION_TIME.getConfigPath())
                .isEqualTo(Duration.ofMillis(100L));

        softly.assertThat(underTest.getMaxScriptStackDepth())
                .as(JavaScriptConfig.JavaScriptConfigValue.MAX_SCRIPT_STACK_DEPTH.getConfigPath())
                .isEqualTo(1);
    }
}
