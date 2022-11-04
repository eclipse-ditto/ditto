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
package org.eclipse.ditto.internal.utils.tracing.filter;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.internal.utils.tracing.span.SpanOperationName;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import kamon.util.Filter;
import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link KamonTracingFilter}.
 */
public final class KamonTracingFilterTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void assertImmutability() {
        assertInstancesOf(
                KamonTracingFilter.class,
                areImmutable(),

                // After a manual check of the implementations of `Filter` it
                // appears to be safe to assume that `Filter` is immutable.
                provided(Config.class, Filter.class).areAlsoImmutable()
        );
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(KamonTracingFilter.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void fromConfigWithNullConfigThrowsNullPointerException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> KamonTracingFilter.fromConfig(null))
                .withMessage("The config must not be null!")
                .withNoCause();
    }

    @Test
    public void fromConfigWithEmptyConfigThrowsIllegalArgumentException() {
        final var kamonTracingFilterResult = KamonTracingFilter.fromConfig(ConfigFactory.empty());

        Assertions.assertThatIllegalArgumentException()
                .isThrownBy(kamonTracingFilterResult::orElseThrow)
                .withMessage("Configuration is missing <includes> and <excludes> paths.")
                .withNoCause();
    }

    @Test
    public void fromConfigWithConfigContainingIncludesOnlyWorksAsExpected() {
        final var includedOperationNames = List.of("foo", "bar", "baz");
        final var kamonTracingFilterResult =
                KamonTracingFilter.fromConfig(ConfigFactory.parseMap(Map.of("includes", includedOperationNames)));
        final var underTest = kamonTracingFilterResult.orElseThrow();

        includedOperationNames.stream()
                .map(SpanOperationName::of)
                .forEach(operationName -> softly.assertThat(underTest.accept(operationName))
                        .as(operationName.toString())
                        .isTrue());
        softly.assertThat(underTest.accept(SpanOperationName.of("zoeglfrex"))).isFalse();
    }

    @Test
    public void fromConfigWithConfigContainingIncludesAndExcludesWorksAsExpected() {
        final var includes = List.of("foo", "bar", "baz", "c**");
        final var excludedOperationNames = List.of("bar", "baz", "chaos");
        final var kamonTracingFilterResult = KamonTracingFilter.fromConfig(ConfigFactory.parseMap(Map.of(
                        "includes", includes,
                        "excludes", excludedOperationNames
                )));
        final var underTest = kamonTracingFilterResult.orElseThrow();

        List.of("foo", "bar", "baz", "create", "chaos", "count").forEach(
                operationName -> softly.assertThat(underTest.accept(SpanOperationName.of(operationName)))
                        .as(operationName)
                        .isEqualTo(!excludedOperationNames.contains(operationName))
        );
        softly.assertThat(underTest.accept(SpanOperationName.of("zoeglfrex"))).isFalse();
    }

    // This is probably the mostly used scenario.
    @Test
    public void fromConfigWithConfigContainingKleeneStarAsIncludesAndSomeExcludesWorksAsExpected() {
        final var excludes = List.of("evil", "war");
        final var kamonTracingFilterTry = KamonTracingFilter.fromConfig(ConfigFactory.parseMap(Map.of(
                "includes", List.of("*"),
                "excludes", excludes
        )));
        final var kamonTracingFilter = kamonTracingFilterTry.orElseThrow();

        excludes.forEach(
                excluded -> softly.assertThat(kamonTracingFilter.accept(SpanOperationName.of(excluded)))
                        .as("excluded %s", excluded)
                        .isFalse()
        );
        Stream.of("ajfka", "faf90qef9io0a", "cjc_ojfew9f").forEach(
                included ->  softly.assertThat(kamonTracingFilter.accept(SpanOperationName.of(included)))
                        .as("included %s", included)
                        .isTrue()
        );
    }

}