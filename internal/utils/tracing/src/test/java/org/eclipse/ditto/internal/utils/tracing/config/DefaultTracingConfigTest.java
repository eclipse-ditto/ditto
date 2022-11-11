/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.tracing.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;
import java.util.Map;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.tracing.filter.AcceptAllTracingFilter;
import org.eclipse.ditto.internal.utils.tracing.filter.KamonTracingFilter;
import org.eclipse.ditto.internal.utils.tracing.filter.TracingFilter;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultTracingConfig}.
 */
public final class DefaultTracingConfigTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultTracingConfig.class, areImmutable(), provided(TracingFilter.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultTracingConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void ofWithEmptyConfigReturnsDefaultConfigValues() {
        final var underTest = DefaultTracingConfig.of(ConfigFactory.empty());

        try (final var softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(underTest.isTracingEnabled()).as("tracing disabled").isFalse();
            softly.assertThat(underTest.getPropagationChannel()).as("propagation channel").isEqualTo("default");
            softly.assertThat(underTest.getTracingFilter())
                    .as("tracing filter")
                    .isInstanceOf(AcceptAllTracingFilter.class);
        }
    }

    @Test
    public void ofWithConfigWithFilterReturnsExpectedTracingFilter() {
        final var includes = List.of("*");
        final var excludes = List.of("foo", "bar");
        final var filterConfigMap = Map.of(
                "includes", includes,
                "excludes", excludes
        );
        final var underTest = DefaultTracingConfig.of(ConfigFactory.parseMap(Map.of(
                "tracing.filter", filterConfigMap
        )));

        assertThat(underTest.getTracingFilter())
                .isEqualTo(KamonTracingFilter.fromConfig(ConfigFactory.parseMap(filterConfigMap)).orElseThrow());
    }

    @Test
    public void ofWithConfigWithInvalidFilterThrowsDittoConfigError() {
        final var filterConfigMap = Map.of("foo", "bar");

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> DefaultTracingConfig.of(
                        ConfigFactory.parseMap(Map.of("tracing.filter", filterConfigMap))
                ))
                .withMessage("Failed to get TracingFilter from config: Configuration is missing <includes> and" +
                        " <excludes> paths.")
                .withCauseInstanceOf(IllegalArgumentException.class);

    }

}