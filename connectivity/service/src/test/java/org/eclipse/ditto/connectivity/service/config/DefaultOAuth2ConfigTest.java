/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.config;

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

public final class DefaultOAuth2ConfigTest {

    private static Config config;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        config = ConfigFactory.load("oauth2-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultOAuth2Config.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultOAuth2Config.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final OAuth2Config underTest = DefaultOAuth2Config.of(config);

        softly.assertThat(underTest.getMaxClockSkew())
                .describedAs(OAuth2Config.ConfigValue.MAX_CLOCK_SKEW.getConfigPath())
                .isEqualTo(Duration.ofHours(1));

        softly.assertThat(underTest.shouldEnforceHttps())
                .describedAs(OAuth2Config.ConfigValue.ENFORCE_HTTPS.getConfigPath())
                .isEqualTo(false);

        softly.assertThat(underTest.getTokenCacheConfig())
                .isNotNull();
        softly.assertThat(underTest.getTokenCacheConfig().getMaximumSize())
                .isEqualTo(1);
    }

}
