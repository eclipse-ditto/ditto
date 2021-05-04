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
package org.eclipse.ditto.base.service.config;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests the default implementation of {@link ThrottlingConfig}.
 */
public final class DefaultThrottlingConfigTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultThrottlingConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultThrottlingConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final ThrottlingConfig underTest = ThrottlingConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getInterval())
                .as(ThrottlingConfig.ConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(ThrottlingConfig.ConfigValue.INTERVAL.getDefaultValue());

        softly.assertThat(underTest.getLimit())
                .as(ThrottlingConfig.ConfigValue.LIMIT.getConfigPath())
                .isEqualTo(ThrottlingConfig.ConfigValue.LIMIT.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final ThrottlingConfig underTest = ThrottlingConfig.of(ConfigFactory.load("throttling-test"));

        softly.assertThat(underTest.getInterval())
                .as(ThrottlingConfig.ConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofSeconds(1234L));

        softly.assertThat(underTest.getLimit())
                .as(ThrottlingConfig.ConfigValue.LIMIT.getConfigPath())
                .isEqualTo(5678);
    }

    @Test
    public void render() {
        final ThrottlingConfig underTest = ThrottlingConfig.of(ConfigFactory.load("throttling-test"));
        final Config rendered = underTest.render();
        final ThrottlingConfig reconstructed = ThrottlingConfig.of(rendered);
        assertThat(reconstructed).isEqualTo(underTest);
    }
}
