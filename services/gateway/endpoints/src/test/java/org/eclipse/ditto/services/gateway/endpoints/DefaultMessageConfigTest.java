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
package org.eclipse.ditto.services.gateway.endpoints;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.services.gateway.util.config.endpoints.DefaultMessageConfig;
import org.eclipse.ditto.services.gateway.util.config.endpoints.MessageConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.gateway.util.config.endpoints.DefaultMessageConfig}.
 */
public final class DefaultMessageConfigTest {

    private static Config messageTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        messageTestConfig = ConfigFactory.load("message-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultMessageConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultMessageConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultMessageConfig underTest = DefaultMessageConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getDefaultTimeout())
                .as(MessageConfig.MessageConfigValue.DEFAULT_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(10L));
        softly.assertThat(underTest.getMaxTimeout())
                .as(MessageConfig.MessageConfigValue.MAX_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(60L));
    }

    @Test
    public void underTestReturnsValuesOfBaseConfig() {
        final DefaultMessageConfig underTest = DefaultMessageConfig.of(messageTestConfig);

        softly.assertThat(underTest.getDefaultTimeout())
                .as(MessageConfig.MessageConfigValue.DEFAULT_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(23L));
        softly.assertThat(underTest.getMaxTimeout())
                .as(MessageConfig.MessageConfigValue.MAX_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(42L));
    }

}
