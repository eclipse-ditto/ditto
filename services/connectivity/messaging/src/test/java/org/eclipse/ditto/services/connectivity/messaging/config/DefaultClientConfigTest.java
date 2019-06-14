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
package org.eclipse.ditto.services.connectivity.messaging.config;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
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
 * Unit test for {@link org.eclipse.ditto.services.connectivity.messaging.config.DefaultClientConfig}.
 */
public final class DefaultClientConfigTest {

    private static Config clientTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        clientTestConf = ConfigFactory.load("client-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultClientConfig.class,
                areImmutable(),
                provided(ClientConfig.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultClientConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultClientConfig underTest = DefaultClientConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getInitTimeout())
                .as(ClientConfig.ClientConfigValue.INIT_TIMEOUT.getConfigPath())
                .isEqualTo(ClientConfig.ClientConfigValue.INIT_TIMEOUT.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultClientConfig underTest = DefaultClientConfig.of(clientTestConf);

        softly.assertThat(underTest.getInitTimeout())
                .as(ClientConfig.ClientConfigValue.INIT_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(1L));
    }
}
