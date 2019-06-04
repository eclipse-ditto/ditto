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
package org.eclipse.ditto.services.concierge.common;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.services.utils.cache.config.CacheConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultCachesConfig}.
 */
public final class DefaultCachesConfigTest {

    private static Config cachesTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        cachesTestConf = ConfigFactory.load("caches-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultCachesConfig.class,
                areImmutable(),
                provided(CachesConfig.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultCachesConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultCachesConfig underTest = DefaultCachesConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getAskTimeout())
                .as("getAskTimeout")
                .isEqualTo(CachesConfig.CachesConfigValue.ASK_TIMEOUT.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultCachesConfig underTest = DefaultCachesConfig.of(cachesTestConf);

        softly.assertThat(underTest.getAskTimeout())
                .as(CachesConfig.CachesConfigValue.ASK_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(30L));

        softly.assertThat(underTest.getEnforcerCacheConfig())
                .as("enforcerCacheConfig")
                .satisfies(enforcerCacheConfig -> {
                    softly.assertThat(enforcerCacheConfig.getMaximumSize())
                            .as(CacheConfig.CacheConfigValue.MAXIMUM_SIZE.getConfigPath())
                            .isEqualTo(20000);
                    softly.assertThat(enforcerCacheConfig.getExpireAfterWrite())
                            .as(CacheConfig.CacheConfigValue.EXPIRE_AFTER_WRITE.getConfigPath())
                            .isEqualTo(Duration.ofMinutes(15L));
                });

        softly.assertThat(underTest.getIdCacheConfig())
                .as("idCacheConfig")
                .satisfies(idCacheConfig -> {
                    softly.assertThat(idCacheConfig.getMaximumSize())
                            .as(CacheConfig.CacheConfigValue.MAXIMUM_SIZE.getConfigPath())
                            .isEqualTo(80000);
                    softly.assertThat(idCacheConfig.getExpireAfterWrite())
                            .as(CacheConfig.CacheConfigValue.EXPIRE_AFTER_WRITE.getConfigPath())
                            .isEqualTo(Duration.ofMinutes(15L));
                });
    }
}
