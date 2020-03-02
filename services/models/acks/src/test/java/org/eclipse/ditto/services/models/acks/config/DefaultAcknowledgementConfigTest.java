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
package org.eclipse.ditto.services.models.acks.config;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig.AcknowledgementConfigValue;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultAcknowledgementConfig}.
 */
public final class DefaultAcknowledgementConfigTest {

    private static Config acknowledgementConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        acknowledgementConf = ConfigFactory.load("acknowledgement-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultAcknowledgementConfig.class,
                areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultAcknowledgementConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultAcknowledgementConfig underTest = DefaultAcknowledgementConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getForwarderFallbackTimeout())
                .as(AcknowledgementConfigValue.FORWARDER_FALLBACK_TIMEOUT.getConfigPath())
                .isEqualTo(AcknowledgementConfigValue.FORWARDER_FALLBACK_TIMEOUT.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultAcknowledgementConfig underTest = DefaultAcknowledgementConfig.of(acknowledgementConf);

        softly.assertThat(underTest.getForwarderFallbackTimeout())
                .as(AcknowledgementConfigValue.FORWARDER_FALLBACK_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(23L));
    }

}
