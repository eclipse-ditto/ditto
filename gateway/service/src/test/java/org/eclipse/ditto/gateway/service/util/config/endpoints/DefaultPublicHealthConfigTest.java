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
package org.eclipse.ditto.gateway.service.util.config.endpoints;

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
 * Unit test for {@link DefaultPublicHealthConfig}.
 */
public final class DefaultPublicHealthConfigTest {

    private static Config publicHealthTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        publicHealthTestConfig = ConfigFactory.load("public-health-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultPublicHealthConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultPublicHealthConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultPublicHealthConfig underTest = DefaultPublicHealthConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getCacheTimeout())
                .as(PublicHealthConfig.PublicHealthConfigValue.CACHE_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(20L));
    }

    @Test
    public void underTestReturnsValuesOfBaseConfig() {
        final DefaultPublicHealthConfig underTest = DefaultPublicHealthConfig.of(publicHealthTestConfig);

        softly.assertThat(underTest.getCacheTimeout())
                .as(PublicHealthConfig.PublicHealthConfigValue.CACHE_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(200L));
    }

}
