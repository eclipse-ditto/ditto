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
package org.eclipse.ditto.services.utils.cache.config;

import static org.assertj.core.api.Assertions.assertThat;
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

import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.utils.cache.config.DefaultCacheConfig}.
 */
public final class DefaultCacheConfigTest {

    private static final String KNOWN_CONFIG_PATH = "my-cache";

    private static Config cacheTestConfig;

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
    public void testSerializationAndDeserialization() throws IOException, ClassNotFoundException {
        final DefaultCacheConfig underTest = DefaultCacheConfig.getInstance(cacheTestConfig, KNOWN_CONFIG_PATH);

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ObjectOutput objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(underTest);
        objectOutputStream.close();

        final byte[] underTestSerialized = byteArrayOutputStream.toByteArray();

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(underTestSerialized);
        final ObjectInput objectInputStream = new ObjectInputStream(byteArrayInputStream);
        final Object underTestDeserialized = objectInputStream.readObject();

        assertThat(underTestDeserialized).isEqualTo(underTest);
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultCacheConfig underTest = DefaultCacheConfig.getInstance(ConfigFactory.empty(), KNOWN_CONFIG_PATH);

        assertThat(underTest.getMaximumSize())
                .as("maximumSize")
                .isEqualTo(CacheConfig.CacheConfigValue.MAXIMUM_SIZE.getDefaultValue());
        assertThat(underTest.getExpireAfterWrite())
                .as("expireAfterWrite")
                .isEqualTo(CacheConfig.CacheConfigValue.EXPIRE_AFTER_WRITE.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultCacheConfig underTest = DefaultCacheConfig.getInstance(cacheTestConfig, KNOWN_CONFIG_PATH);

        assertThat(underTest.getMaximumSize())
                .as("maximumSize")
                .isEqualTo(4711);
        assertThat(underTest.getExpireAfterWrite())
                .as("expireAfterWrite")
                .isEqualTo(Duration.ofMinutes(3));
    }

}