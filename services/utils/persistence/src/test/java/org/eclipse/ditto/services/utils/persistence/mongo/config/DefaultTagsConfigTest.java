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
package org.eclipse.ditto.services.utils.persistence.mongo.config;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultTagsConfig}.
 */
public final class DefaultTagsConfigTest {

    private static Config snapshotTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        snapshotTestConf = ConfigFactory.load("tags-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultTagsConfig.class,
                areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultTagsConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultTagsConfig underTest = DefaultTagsConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getStreamingCacheSize())
                .as(TagsConfig.TagsConfigValue.STREAMING_CACHE_SIZE.getConfigPath())
                .isEqualTo(TagsConfig.TagsConfigValue.STREAMING_CACHE_SIZE.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultTagsConfig underTest = DefaultTagsConfig.of(snapshotTestConf);

        softly.assertThat(underTest.getStreamingCacheSize())
                .as(TagsConfig.TagsConfigValue.STREAMING_CACHE_SIZE.getConfigPath())
                .isEqualTo(100);
    }

}
