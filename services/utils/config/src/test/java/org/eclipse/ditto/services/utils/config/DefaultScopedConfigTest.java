/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;

import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.utils.config.DefaultScopedConfig}.
 */
public final class DefaultScopedConfigTest {

    private static final String KNOWN_CONFIG_PATH = "nowhere";

    private static Config testConfig;

    @BeforeClass
    public static void initTestFixture() {
        testConfig = ConfigFactory.load("test.conf");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultScopedConfig.class,
                areImmutable(),
                provided(Config.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultScopedConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void tryToCreateInstanceWithNullOriginalConfig() {
        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> DefaultScopedConfig.newInstance((Config) null, KNOWN_CONFIG_PATH))
                .withCause(new NullPointerException("The original Config must not be null!"));
    }

    @Test
    public void tryToCreateInstanceWithNullConfigPath() {
        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> DefaultScopedConfig.newInstance(ConfigFactory.empty(), null))
                .withCause(new NullPointerException("The config path must not be null!"));
    }

    @Test
    public void tryToGetInstanceWithMissingConfigAtConfigPath() {
        final Config config = ConfigFactory.parseMap(Collections.singletonMap("foo", "bar"));

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> DefaultScopedConfig.newInstance(config, KNOWN_CONFIG_PATH))
                .withMessage("Failed to get nested Config at <%s>!", KNOWN_CONFIG_PATH)
                .withCauseInstanceOf(ConfigException.Missing.class);
    }

    @Test
    public void tryToGetInstanceWithConfigWithWrongTypeAtConfigPath() {
        final Config config = ConfigFactory.parseMap(Collections.singletonMap(KNOWN_CONFIG_PATH, "bar"));

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> DefaultScopedConfig.newInstance(config, KNOWN_CONFIG_PATH))
                .withMessage("Failed to get nested Config at <%s>!", KNOWN_CONFIG_PATH)
                .withCauseInstanceOf(ConfigException.WrongType.class);
    }

    @Test
    public void getConfigPathReturnsRelativePathIfDefaultScopedConfigIsBuiltFromPlainConfig() {
        final DefaultScopedConfig underTest = DefaultScopedConfig.newInstance(testConfig, "ditto");

        assertThat(underTest.getConfigPath()).isEqualTo("ditto");
    }

    @Test
    public void getConfigPathReturnsAbsolutePathIfDefaultScopedConfigIsBuiltFromScopedConfig() {
        final Config dittoScopedConfig = DefaultScopedConfig.newInstance(testConfig, "ditto");
        final DefaultScopedConfig underTest = DefaultScopedConfig.newInstance(dittoScopedConfig, "concierge");

        assertThat(underTest.getConfigPath()).isEqualTo("ditto.concierge");
    }

    @Test
    public void tryToGetMissingValue() {
        final DefaultScopedConfig underTest = DefaultScopedConfig.empty("ditto");

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> underTest.getInt(KNOWN_CONFIG_PATH))
                .withMessage("Failed to get int value for path <%s>!", "ditto." + KNOWN_CONFIG_PATH)
                .withCauseInstanceOf(ConfigException.Missing.class);
    }

}