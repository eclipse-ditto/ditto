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
package org.eclipse.ditto.internal.utils.cache.config;

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
 * Unit test for {@link DefaultCacheConfig}.
 */
public final class DefaultCacheConfigTest {

    private static final String KNOWN_CONFIG_PATH = "my-cache";

    private static Config cacheTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        cacheTestConfig = ConfigFactory.load("cache-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultCacheConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultCacheConfig.class)
                .usingGetClass()
                .withNonnullFields("expireAfterWrite")
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultCacheConfig underTest = DefaultCacheConfig.of(ConfigFactory.empty(), KNOWN_CONFIG_PATH);

        softly.assertThat(underTest.getMaximumSize())
                .as(CacheConfig.CacheConfigValue.MAXIMUM_SIZE.getConfigPath())
                .isEqualTo(CacheConfig.CacheConfigValue.MAXIMUM_SIZE.getDefaultValue());
        softly.assertThat(underTest.getExpireAfterWrite())
                .as(CacheConfig.CacheConfigValue.EXPIRE_AFTER_WRITE.getConfigPath())
                .isEqualTo(CacheConfig.CacheConfigValue.EXPIRE_AFTER_WRITE.getDefaultValue());
        softly.assertThat(underTest.getExpireAfterAccess())
                .as(CacheConfig.CacheConfigValue.EXPIRE_AFTER_ACCESS.getConfigPath())
                .isEqualTo(CacheConfig.CacheConfigValue.EXPIRE_AFTER_ACCESS.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultCacheConfig underTest = DefaultCacheConfig.of(cacheTestConfig, KNOWN_CONFIG_PATH);

        softly.assertThat(underTest.getMaximumSize())
                .as(CacheConfig.CacheConfigValue.MAXIMUM_SIZE.getConfigPath())
                .isEqualTo(4711);
        softly.assertThat(underTest.getExpireAfterWrite())
                .as(CacheConfig.CacheConfigValue.EXPIRE_AFTER_WRITE.getConfigPath())
                .isEqualTo(Duration.ofMinutes(3));
        softly.assertThat(underTest.getExpireAfterAccess())
                .as(CacheConfig.CacheConfigValue.EXPIRE_AFTER_ACCESS.getConfigPath())
                .isEqualTo(Duration.ofMinutes(4));
    }

}
