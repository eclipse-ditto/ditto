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

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultIndexInitializationConfig}.
 */
public final class DefaultIndexInitializationConfigTest {

    private static Config indexInitializationTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        indexInitializationTestConf = ConfigFactory.load("index-initialization-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultIndexInitializationConfig.class,
                areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultIndexInitializationConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultIndexInitializationConfig underTest = DefaultIndexInitializationConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.isIndexInitializationConfigEnabled())
                .as(IndexInitializationConfig.IndexInitializerConfigValue.ENABLED.getConfigPath())
                .isEqualTo(IndexInitializationConfig.IndexInitializerConfigValue.ENABLED.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultIndexInitializationConfig underTest = DefaultIndexInitializationConfig.of(
                indexInitializationTestConf);

        softly.assertThat(underTest.isIndexInitializationConfigEnabled())
                .as(IndexInitializationConfig.IndexInitializerConfigValue.ENABLED.getConfigPath())
                .isTrue();
    }
}
