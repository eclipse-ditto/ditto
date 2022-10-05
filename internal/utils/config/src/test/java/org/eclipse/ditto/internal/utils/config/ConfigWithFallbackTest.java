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
package org.eclipse.ditto.internal.utils.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link ConfigWithFallback}.
 */
public final class ConfigWithFallbackTest {

    private static final String KNOWN_CONFIG_PATH = "nowhere";

    private static Config testConfig;

    @BeforeClass
    public static void initTestFixture() {
        testConfig = ConfigFactory.load("test.conf");
    }


    @Test
    public void assertImmutability() {
        assertInstancesOf(ConfigWithFallback.class,
                areImmutable(),
                provided(Config.class).isAlsoImmutable());
    }

    @Test
    public void tryToCreateInstanceWithNullOriginalConfig() {
        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> ConfigWithFallback.newInstance(null, KNOWN_CONFIG_PATH, new KnownConfigValue[0]))
                .withCause(new NullPointerException("The original Config must not be null!"));
    }

    @Test
    public void tryToCreateInstanceWithNullConfigPath() {
        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> ConfigWithFallback.newInstance(ConfigFactory.empty(), null, new KnownConfigValue[0]))
                .withCause(new NullPointerException("The config path must not be null!"));
    }

    @Test
    public void tryToCreateInstanceWithNullFallBackValues() {
        final Config config = ConfigFactory.parseMap(Collections.singletonMap(KNOWN_CONFIG_PATH, "nothing"));

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> ConfigWithFallback.newInstance(config, KNOWN_CONFIG_PATH, null))
                .withCause(new NullPointerException("The fall-back values must not be null!"));
    }

    @Test
    public void tryToGetInstanceWithConfigWithWrongTypeAtConfigPath() {
        final Config config = ConfigFactory.parseMap(Collections.singletonMap(KNOWN_CONFIG_PATH, "bar"));

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> ConfigWithFallback.newInstance(config, KNOWN_CONFIG_PATH, new KnownConfigValue[0]))
                .withMessage("Failed to get nested Config at <%s>!", KNOWN_CONFIG_PATH)
                .withCauseInstanceOf(ConfigException.WrongType.class);
    }

    @Test
    public void useFallBackValuesIfConfigHasNoNestedConfigAtExpectedPath() {
        final Config config = ConfigFactory.parseMap(Collections.singletonMap("foo", "bar"));

        final KnownConfigValue barFallbackValue = Mockito.mock(KnownConfigValue.class);
        Mockito.when(barFallbackValue.getConfigPath()).thenReturn("bar");
        Mockito.when(barFallbackValue.getDefaultValue()).thenReturn(1);

        final ConfigWithFallback underTest =
                ConfigWithFallback.newInstance(config, KNOWN_CONFIG_PATH, new KnownConfigValue[]{barFallbackValue});

        assertThat(underTest.root()).satisfies(configObject -> assertThat(configObject).hasSize(1));
        assertThat(underTest.getInt(barFallbackValue.getConfigPath())).isEqualTo(barFallbackValue.getDefaultValue());
    }

    @Test
    public void configWithEmptyFallBackValuesRemainsSameUnwrapped() {
        final Map<String, Object> configValueMap = new HashMap<>();
        configValueMap.put("foo", "bar");
        configValueMap.put("bar", 1);
        configValueMap.put("baz", true);
        final Config config = ConfigFactory.parseMap(Collections.singletonMap(KNOWN_CONFIG_PATH, configValueMap));

        final ConfigWithFallback underTest =
                ConfigWithFallback.newInstance(config, KNOWN_CONFIG_PATH, new KnownConfigValue[0]);

        assertThat(underTest.root()).satisfies(configObject -> assertThat(configObject).hasSize(3));
        assertThat(underTest.getString("foo")).isEqualTo("bar");
        assertThat(underTest.getInt("bar")).isEqualTo(1);
        assertThat(underTest.getBoolean("baz")).isTrue();
    }

    @Test
    public void useFallBackValuesIfNotSetInOriginalConfig() {
        final Map<String, Object> configValueMap = new HashMap<>();
        configValueMap.put("foo", "bar");
        configValueMap.put("baz", true);
        final Config config = ConfigFactory.parseMap(Collections.singletonMap(KNOWN_CONFIG_PATH, configValueMap));

        final KnownConfigValue barFallbackValue = Mockito.mock(KnownConfigValue.class);
        Mockito.when(barFallbackValue.getConfigPath()).thenReturn("bar");
        Mockito.when(barFallbackValue.getDefaultValue()).thenReturn(1);

        final ConfigWithFallback underTest =
                ConfigWithFallback.newInstance(config, KNOWN_CONFIG_PATH, new KnownConfigValue[]{barFallbackValue});

        assertThat(underTest.root()).satisfies(configObject -> {
            assertThat(configObject).hasSize(3);
        });
        assertThat(underTest.getString("foo")).isEqualTo("bar");
        assertThat(underTest.getInt("bar")).isEqualTo(1);
        assertThat(underTest.getBoolean("baz")).isTrue();
    }

    @Test
    public void ignoreFallBackValuesIfSetInOriginalConfig() {
        final Map<String, Object> configValueMap = new HashMap<>();
        configValueMap.put("foo", "bar");
        configValueMap.put("baz", true);
        configValueMap.put("bar", 2);
        final Config config = ConfigFactory.parseMap(Collections.singletonMap(KNOWN_CONFIG_PATH, configValueMap));

        final KnownConfigValue barFallbackValue = Mockito.mock(KnownConfigValue.class);
        Mockito.when(barFallbackValue.getConfigPath()).thenReturn("bar");
        Mockito.when(barFallbackValue.getDefaultValue()).thenReturn(1);

        final ConfigWithFallback underTest =
                ConfigWithFallback.newInstance(config, KNOWN_CONFIG_PATH, new KnownConfigValue[]{barFallbackValue});

        assertThat(underTest.root()).satisfies(configObject -> {
            assertThat(configObject).hasSize(3);
        });
        assertThat(underTest.getString("foo")).isEqualTo("bar");
        assertThat(underTest.getInt("bar")).isEqualTo(2);
        assertThat(underTest.getBoolean("baz")).isTrue();
    }

    @Test
    public void getConfigPathReturnsRelativePathIfConfigWithFallbackIsBuiltFromPlainConfig() {
        final ConfigWithFallback underTest =
                ConfigWithFallback.newInstance(testConfig, "ditto", new KnownConfigValue[0]);

        assertThat(underTest.getConfigPath()).isEqualTo("ditto");
    }

    @Test
    public void getConfigPathReturnsAbsolutePathIfDefaultScopedConfigIsBuiltFromScopedConfig() {
        final Config dittoScopedConfig = DefaultScopedConfig.dittoScoped(testConfig);
        final ConfigWithFallback underTest =
                ConfigWithFallback.newInstance(dittoScopedConfig, "gateway", new KnownConfigValue[0]);

        assertThat(underTest.getConfigPath()).isEqualTo("ditto.gateway");
    }

}
