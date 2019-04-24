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
package org.eclipse.ditto.services.concierge.cache.config;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
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
 * Unit test for {@link org.eclipse.ditto.services.concierge.cache.config.DefaultCachesConfig}.
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
    public void testSerializationAndDeserialization() throws IOException, ClassNotFoundException {
        final DefaultCachesConfig underTest = DefaultCachesConfig.of(cachesTestConf);

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ObjectOutput objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(underTest);
        objectOutputStream.close();

        final byte[] underTestSerialized = byteArrayOutputStream.toByteArray();

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(underTestSerialized);
        final ObjectInput objectInputStream = new ObjectInputStream(byteArrayInputStream);
        final Object underTestDeserialized = objectInputStream.readObject();

        softly.assertThat(underTestDeserialized).isEqualTo(underTest);
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
