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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.List;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.services.utils.config.DittoConfigError;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultSuffixBuilderConfig}.
 */
public final class DefaultSuffixBuilderConfigTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultSuffixBuilderConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultSuffixBuilderConfig.class)
                .usingGetClass()
                .withNonnullFields("supportedPrefixes")
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
                SuffixBuilderConfig.SuffixBuilderConfigValue.EXTRACTOR_CLASS.getConfigPath();
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

        softly.assertThat(underTest.getSupportedPrefixes()).isEmpty();
    }

    @Test
    public void getSupportedPrefixesReturnsConfigured() {
        final String absoluteSupportedPrefixesPath = DefaultSuffixBuilderConfig.CONFIG_PATH + "." +
                SuffixBuilderConfig.SuffixBuilderConfigValue.SUPPORTED_PREFIXES.getConfigPath();
        final List<String> supportedPrefixes = Collections.singletonList("chronophone");
        final Config config =
                ConfigFactory.parseMap(Collections.singletonMap(absoluteSupportedPrefixesPath, supportedPrefixes));

        final DefaultSuffixBuilderConfig underTest = DefaultSuffixBuilderConfig.of(config);

        softly.assertThat(underTest.getSupportedPrefixes()).isEqualTo(supportedPrefixes);
    }

    @Test
    public void toStringContainsExpected() {
        final DefaultSuffixBuilderConfig underTest = DefaultSuffixBuilderConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.toString()).contains(underTest.getClass().getSimpleName()).contains("supportedPrefixes");
    }

}