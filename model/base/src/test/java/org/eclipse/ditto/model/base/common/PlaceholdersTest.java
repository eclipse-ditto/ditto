/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.base.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests {@link Placeholders}.
 */
public class PlaceholdersTest {

    private static final String REPLACER_KEY_1 = "my:arbitrary:replacer1";
    private static final String REPLACER_1 = "{{ " + REPLACER_KEY_1 + " }}";
    private static final String LEGACY_REPLACER_1 = "${my.arbitrary.replacer1}";
    private static final String REPLACED_1 = "firstReplaced";
    private static final String REPLACER_KEY_2 = "my:arbitrary:replacer2";
    private static final String REPLACER_2 = "{{ " + REPLACER_KEY_2 + " }}";
    private static final String LEGACY_REPLACER_2 = "${my.arbitrary.replacer2}";
    private static final String REPLACED_2 = "secondReplaced";

    private static final String UNKNOWN_REPLACER_KEY = "unknown:unknown";
    private static final String UNKNOWN_REPLACER = "{{ " + UNKNOWN_REPLACER_KEY + " }}";
    private static final String UNKNOWN_LEGACY_REPLACER_KEY = "unknown.unknown";
    private static final String UNKNOWN_LEGACY_REPLACER = "${" + UNKNOWN_LEGACY_REPLACER_KEY + "}";

    private Function<String, String> replacerFunction;

    @Before
    public void init() {
        final Map<String, String> replacementDefinitions = new LinkedHashMap<>();
        replacementDefinitions.put(REPLACER_KEY_1, REPLACED_1);
        replacementDefinitions.put(REPLACER_KEY_2, REPLACED_2);
        replacerFunction = replacementDefinitions::get;
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(Placeholders.class, areImmutable());
    }

    @Test
    public void substituteFailsWithNullInput() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> Placeholders.substitute(null, replacerFunction));
    }

    @Test
    public void substituteFailsWithNullReplacerFunction() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> Placeholders.substitute("doesNotMatter", null));
    }

    @Test
    public void substituteReturnsInputWhenInputDoesNotContainReplacers() {
        final String input = "withoutReplacers";

        final String substituted = Placeholders.substitute(input, replacerFunction);

        assertThat(substituted).isSameAs(input);
    }

    @Test
    public void substituteReturnsReplacedWhenInputContainsOnlyPlaceholder() {
        final String substituted = Placeholders.substitute(REPLACER_1, replacerFunction);

        assertThat(substituted).isEqualTo(REPLACED_1);
    }

    @Test
    public void substituteReturnsReplacedInputWhenInputContainsSurroundedPlaceholder() {
        final String substituted =
                Placeholders.substitute("a" + REPLACER_1 + "z", replacerFunction);

        assertThat(substituted).isEqualTo("a" + REPLACED_1 + "z");
    }

    @Test
    public void substituteReturnsReplacedInputWhenInputContainsMultiplePlaceholders() {
        final String input = "a" + REPLACER_1 + "b" + REPLACER_2 + "c";

        final String substituted = Placeholders.substitute(input, replacerFunction);

        final String expectedOutput = "a" + REPLACED_1 + "b" + REPLACED_2 + "c";
        assertThat(substituted).isEqualTo(expectedOutput);
    }

    @Test
    public void substituteReturnsReplacedWhenInputContainsOnlyLegacyPlaceholder() {
        final String substituted =
                Placeholders.substitute(LEGACY_REPLACER_1, replacerFunction);

        assertThat(substituted).isEqualTo(REPLACED_1);
    }

    @Test
    public void substituteReturnsReplacedInputWhenInputContainsBothNewAndLegacyPlaceholders() {
        final String input = "a" + REPLACER_1 + "b" + LEGACY_REPLACER_2 + "c";

        final String substituted = Placeholders.substitute(input, replacerFunction);

        final String expectedOutput = "a" + REPLACED_1 + "b" + REPLACED_2 + "c";
        assertThat(substituted).isEqualTo(expectedOutput);
    }

    @Test
    public void substituteThrowsWhenReplacerFunctionReturnsNullForPlaceholder() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> Placeholders.substitute(UNKNOWN_REPLACER, (placeholder) -> null));
    }

    @Test
    public void substituteThrowsWhenReplacerFunctionReturnsNullForLegacyPlaceholder() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> Placeholders.substitute(UNKNOWN_LEGACY_REPLACER, (placeholder) -> null));
    }

    @Test
    public void substituteReturnsReplacedInputWhenInputContainsNestedPlaceholder() {
        final String nestedPlaceholder = "{{ " + REPLACER_1 + " }}";

        final String substituted = Placeholders.substitute(nestedPlaceholder, replacerFunction);

        final String expectedOutput = "{{ " +  REPLACED_1 + " }}";
        assertThat(substituted).isEqualTo(expectedOutput);
    }

    /**
     * Nesting legacy-placeholders does not work. In our case, null will be returned by the replacerFunction.
     * Normally, replacerFunctions should throw a DittoRuntimeException in this case.
     */
    @Test
    public void substituteThrowsWhenInputContainsNestedLegacyPlaceholder() {
        final String nestedPlaceholder = "${" + LEGACY_REPLACER_1 + "}";

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> Placeholders.substitute(nestedPlaceholder, (placeholder) -> null));
    }

    @Test
    public void containsReturnsTrueWhenInputContainsPlaceholder() {
        final String input = "a" + REPLACER_1 + "z";

        final boolean contains = Placeholders.containsAnyPlaceholder(input);

        assertThat(contains).isTrue();
    }

    @Test
    public void containsReturnsTrueWhenInputContainsLegacyPlaceholder() {
        final String input = "a" + LEGACY_REPLACER_1 + "z";

        final boolean contains = Placeholders.containsAnyPlaceholder(input);

        assertThat(contains).isTrue();
    }

    @Test
    public void containsReturnsTrueWhenInputContainsBothNewAndLegacyPlaceholders() {
        final String input = "a" + REPLACER_1 + "b" + LEGACY_REPLACER_2 + "c";

        final boolean contains = Placeholders.containsAnyPlaceholder(input);

        assertThat(contains).isTrue();
    }

    @Test
    public void containsReturnsFailsWhenInputDoesNotContainAnyPlaceholder() {
        final String input = "abc";

        final boolean contains = Placeholders.containsAnyPlaceholder(input);

        assertThat(contains).isFalse();
    }
}
