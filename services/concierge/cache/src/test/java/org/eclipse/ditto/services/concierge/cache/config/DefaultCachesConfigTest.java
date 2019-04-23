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

import static org.assertj.core.api.Assertions.assertThat;
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

import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.concierge.cache.config.DefaultCachesConfig}.
 */
public final class DefaultCachesConfigTest {

    private static Config cachesTestConf;

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

        assertThat(underTestDeserialized).isEqualTo(underTest);
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultCachesConfig underTest = DefaultCachesConfig.of(ConfigFactory.empty());

        assertThat(underTest.getAskTimeout())
                .as("getAskTimeout")
                .isEqualTo(CachesConfig.CachesConfigValue.ASK_TIMEOUT.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultCachesConfig underTest = DefaultCachesConfig.of(cachesTestConf);

        assertThat(underTest.getAskTimeout())
                .as("getAskTimeout")
                .isEqualTo(Duration.ofSeconds(30L));

        assertThat(underTest.getEnforcerCacheConfig())
                .as("enforcerCacheConfig")
                .satisfies(enforcerCacheConfig -> {
                    assertThat(enforcerCacheConfig.getMaximumSize())
                            .as("getMaximumSize")
                            .isEqualTo(20000);
                    assertThat(enforcerCacheConfig.getExpireAfterWrite())
                            .as("getExpireAfterWrite")
                            .isEqualTo(Duration.ofMinutes(15L));
                });

        assertThat(underTest.getIdCacheConfig())
                .as("idCacheConfig")
                .satisfies(idCacheConfig -> {
                    assertThat(idCacheConfig.getMaximumSize())
                            .as("getMaximumSize")
                            .isEqualTo(80000);
                    assertThat(idCacheConfig.getExpireAfterWrite())
                            .as("getExpireAfterWrite")
                            .isEqualTo(Duration.ofMinutes(15L));
                });
    }
}
