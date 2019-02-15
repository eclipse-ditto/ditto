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
package org.eclipse.ditto.services.base.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.services.base.config.DefaultSuffixBuilderConfig.SuffixBuilderConfigValue;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.base.config.DefaultSuffixBuilderConfig}.
 */
public final class DefaultSuffixBuilderConfigTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultSuffixBuilderConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultSuffixBuilderConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void tryToGetInstanceWithNullConfig() {
        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> DefaultSuffixBuilderConfig.of((Config) null))
                .withCauseInstanceOf(NullPointerException.class);
    }

    @Test
    public void tryToCreateInstanceWithNonExistingExtractorClass() {
        final String absoluteExtractorClassPath = DefaultSuffixBuilderConfig.CONFIG_PATH + "." +
                SuffixBuilderConfigValue.EXTRACTOR_CLASS.getPath();
        final String extractorClassName = "org.example.test.Chronophone";
        final Config config =
                ConfigFactory.parseMap(Collections.singletonMap(absoluteExtractorClassPath, extractorClassName));

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> DefaultSuffixBuilderConfig.of(config))
                .withMessageContaining(extractorClassName)
                .withMessageContaining("not available at the classpath!")
                .withCauseInstanceOf(ClassNotFoundException.class);
    }

    @Test
    public void getSupportedPrefixesReturnsDefaultValueWhenConfigWasEmpty() {
        final DefaultSuffixBuilderConfig underTest = DefaultSuffixBuilderConfig.of(ConfigFactory.empty());

        assertThat(underTest.getSupportedPrefixes()).isEmpty();
    }

    @Test
    public void getSupportedPrefixesReturnsConfigured() {
        final String absoluteSupportedPrefixesPath = DefaultSuffixBuilderConfig.CONFIG_PATH + "." +
                SuffixBuilderConfigValue.SUPPORTED_PREFIXES.getPath();
        final List<String> supportedPrefixes = Collections.singletonList("chronophone");
        final Config config =
                ConfigFactory.parseMap(Collections.singletonMap(absoluteSupportedPrefixesPath, supportedPrefixes));

        final DefaultSuffixBuilderConfig underTest = DefaultSuffixBuilderConfig.of(config);

        assertThat(underTest.getSupportedPrefixes()).isEqualTo(supportedPrefixes);
    }

    @Test
    public void toStringContainsExpected() {
        final DefaultSuffixBuilderConfig underTest = DefaultSuffixBuilderConfig.of(ConfigFactory.empty());

        assertThat(underTest.toString()).contains(underTest.getClass().getSimpleName()).contains("supportedPrefixes");
    }

}