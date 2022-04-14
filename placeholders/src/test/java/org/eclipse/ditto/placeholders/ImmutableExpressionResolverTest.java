/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.placeholders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mutabilitydetector.unittesting.AllowedReason;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link ImmutableExpressionResolver}.
 */
public class ImmutableExpressionResolverTest {

    private static final Map<String, String> KNOWN_HEADERS =
            DittoHeaders.newBuilder()
                    .putHeader("one", "1")
                    .putHeader("two", "2")
                    .putHeader("splitted", "one two three")
                    .build();
    private static final String UNKNOWN_HEADER_EXPRESSION = "{{ header:missing }}";
    private static final String UNKNOWN_THING_EXPRESSION = "{{ thing:missing }}";
    private static final String UNKNOWN_TOPIC_EXPRESSION = "{{ topic:missing }}";

    private static ImmutableExpressionResolver underTest;

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(ImmutableExpressionResolver.class, MutabilityMatchers.areImmutable(),
                AllowedReason.assumingFields("placeholderResolvers", "placeholderResolverFunctions")
                        .areSafelyCopiedUnmodifiableCollectionsWithImmutableElements()
        );
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableExpressionResolver.class)
                .usingGetClass()
                .verify();
    }

    @BeforeClass
    public static void setupClass() {
        final ImmutablePlaceholderResolver<Map<String, String>> headersResolver =
                new ImmutablePlaceholderResolver<>(PlaceholderFactory.newHeadersPlaceholder(),
                        Collections.singletonList(KNOWN_HEADERS));
        underTest = new ImmutableExpressionResolver(Collections.singletonList(headersResolver));
    }

    @Test
    public void testSuccessfulPlaceholderResolution() {
        assertThat(underTest.resolve("{{ header:one }}")).contains(KNOWN_HEADERS.get("one"));
        assertThat(underTest.resolve("{{ header:two }}")).contains(KNOWN_HEADERS.get("two"));
    }

    @Test
    public void testSuccessfulArrayExpressionResolution() {
        assertThat(underTest.resolve("{{ header:splitted | fn:split(' ') | fn:upper() }}").toStream())
                .containsExactlyElementsOf(Arrays.stream(KNOWN_HEADERS.get("splitted").split(" "))
                        .map(String::toUpperCase)
                        .collect(Collectors.toList())
                );
    }

    @Test
    public void testSuccessfulArrayExpressionResolutionForTwoCombinedExpressions() {
        assertThat(underTest.resolve(
                "{{ header:splitted | fn:split(' ')  }}_{{ header:one }}").toStream()
        ).containsExactlyElementsOf(Arrays.asList(
                "one_1",
                "two_1",
                "three_1"
        ));
    }

    @Test
    public void testSuccessfulArrayExpressionResolutionForTwoCombinedArrayExpressions() {
        assertThat(underTest.resolve(
                "{{ header:splitted | fn:split(' ') | fn:upper() }}:{{ header:splitted | fn:split(' ') | fn:lower() }}")
                .toStream()
        ).containsExactlyElementsOf(Arrays.asList(
                "ONE:one",
                "ONE:two",
                "ONE:three",
                "TWO:one",
                "TWO:two",
                "TWO:three",
                "THREE:one",
                "THREE:two",
                "THREE:three"
        ));
    }

    @Test
    public void testPlaceholderResolutionAllowingUnresolvedPlaceholders() {
        // supported unresolved placeholders are retained
        assertThat(underTest.resolve(UNKNOWN_HEADER_EXPRESSION))
                .isEqualTo(PipelineElement.unresolved());

        // unsupported placeholders cause error
        assertThatExceptionOfType(UnresolvedPlaceholderException.class)
                .isThrownBy(() -> underTest.resolve(UNKNOWN_THING_EXPRESSION));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class)
                .isThrownBy(() -> underTest.resolve(UNKNOWN_TOPIC_EXPRESSION));
    }

    @Test
    public void testUnsuccessfulPlaceholderResolution() {
        assertThat(underTest.resolve(UNKNOWN_HEADER_EXPRESSION)).isEmpty();
    }

    @Test
    public void testSuccessfulFunctionBasedOnPlaceholderInput() {

        assertThat(underTest.resolve("{{ header:unknown | fn:default('fallback') }}"))
                .contains("fallback");
        assertThat(underTest.resolve("{{ header:unknown | fn:default('bar') | fn:upper() }}"))
                .contains("BAR");
        assertThat(underTest.resolve("{{ header:unknown | fn:default(' fallback-spaces  ') }}"))
                .contains(" fallback-spaces  ");

        // verify different whitespace
        assertThat(underTest.resolve("{{header:unknown |fn:default('bar')| fn:upper() }}"))
                .contains("BAR");
        assertThat(underTest.resolve("{{    header:unknown |     fn:default('bar')    |fn:upper()}}"))
                .contains("BAR");
        assertThat(underTest.resolve("{{ header:unknown | fn:default(  'bar'  ) |fn:upper(  ) }}"))
                .contains("BAR");
    }

    @Test
    public void testSpecialCharactersInStrings() {
        assertThat(underTest.resolve("{{ header:unknown | fn:default( ' \\s%!@/*+\"\\'上手カキクケコ' ) | fn:upper( ) }}"))
                .contains(" \\S%!@/*+\"\\'上手カキクケコ");

        assertThat(underTest.resolve("{{ header:unknown | fn:default( \" \\s%!@/*+'\\\"上手カキクケコ\" ) | fn:upper( ) }}"))
                .contains(" \\S%!@/*+'\\\"上手カキクケコ");
    }

    @Test
    public void rejectUnsupportedPlaceholdersWithSpecialCharacters() {
        assertThatExceptionOfType(UnresolvedPlaceholderException.class)
                .isThrownBy(() -> underTest.resolve("{{ thing:id\\s%!@/*+上手カキクケコ }}"));
    }

    @Test
    public void testUnsuccessfulFunctionBasedOnPlaceholderInput() {
        assertThat(underTest.resolve("{{ header:unknown }}")).isEmpty();
        assertThat(underTest.resolve("{{ header:unknown | fn:default(header:unknown) }}")).isEmpty();
    }

    @Test
    public void testSuccessfulSinglePlaceholderResolution() {
        assertThat(underTest.resolveAsPipelineElement("header:one")).contains(KNOWN_HEADERS.get("one"));
    }

    @Test
    public void testSuccessfulArrayPlaceholderResolution() {
        assertThat(underTest.resolveAsPipelineElement("header:splitted | fn:split(' ')").toStream())
                .containsExactlyElementsOf(Arrays.stream(KNOWN_HEADERS.get("splitted").split(" "))
                        .collect(Collectors.toList())
                );
    }

    @Test
    public void testUnsuccessfulSinglePlaceholderResolution() {
        assertThat(underTest.resolveAsPipelineElement("header:unknown"))
                .isEmpty();
        assertThat(underTest.resolveAsPipelineElement("fn:substring-before('')"))
                .isEmpty();
    }

    @Test
    public void testResolveBarelyTooComplexFunctionChainWorks() {
        // 10 functions should work:
        assertThat(underTest.resolve(
                "{{ header:unknown | fn:default('fallback') | fn:upper() | fn:lower() | fn:upper()" +
                        " | fn:lower() | fn:upper() | fn:lower() | fn:upper() | fn:lower() | fn:upper() }}"))
                .contains("FALLBACK");
    }

    @Test
    public void testResolveTooComplexFunctionChainResultsInException() {
        // 11 functions should fail:
        assertThatExceptionOfType(PlaceholderFunctionTooComplexException.class).isThrownBy(() ->
                underTest.resolve(
                        "{{ header:unknown | fn:default('fallback') | fn:upper() | fn:lower()" +
                                " | fn:upper() | fn:lower() | fn:upper() | fn:lower() | fn:upper() | fn:lower()" +
                                " | fn:upper() | fn:lower() }}"
                ));
    }
}
