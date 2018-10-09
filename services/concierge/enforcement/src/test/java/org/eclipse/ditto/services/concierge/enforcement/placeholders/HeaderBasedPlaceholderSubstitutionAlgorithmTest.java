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
package org.eclipse.ditto.services.concierge.enforcement.placeholders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.assertj.core.api.ThrowableAssert;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayPlaceholderNotResolvableException;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests {@link HeaderBasedPlaceholderSubstitutionAlgorithm}.
 */
public class HeaderBasedPlaceholderSubstitutionAlgorithmTest {

    private static final String REPLACER_KEY_1 = "my:arbitrary:replacer1";
    private static final String REPLACER_1 = "{{ " + REPLACER_KEY_1 + " }}";
    private static final String LEGACY_REPLACER_1 = "${my.arbitrary.replacer1}";
    private static final String REPLACED_1 = "firstReplaced";
    private static final String REPLACER_KEY_2 = "my:arbitrary:replacer2";
    private static final String REPLACED_2 = "secondReplaced";

    private static final String UNKNOWN_REPLACER_KEY = "unknown:unknown";
    private static final String UNKNOWN_REPLACER = "{{ " + UNKNOWN_REPLACER_KEY + " }}";
    private static final String UNKNOWN_LEGACY_REPLACER_KEY = "unknown.unknown";
    private static final String UNKNOWN_LEGACY_REPLACER = "${" + UNKNOWN_LEGACY_REPLACER_KEY + "}";

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder().correlationId("foo").build();

    private HeaderBasedPlaceholderSubstitutionAlgorithm underTest;

    @Before
    public void init() {
        final Map<String, Function<DittoHeaders, String>> replacementDefinitions = new LinkedHashMap<>();
        replacementDefinitions.put(REPLACER_KEY_1, (dittoHeaders -> REPLACED_1));
        replacementDefinitions.put(REPLACER_KEY_2, (dittoHeaders -> REPLACED_2));

        underTest = HeaderBasedPlaceholderSubstitutionAlgorithm.newInstance(replacementDefinitions);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(HeaderBasedPlaceholderSubstitutionAlgorithm.class, areImmutable(),
                assumingFields("replacementDefinitions", "knownPlaceHolders")
                        .areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void substituteFailsWithNullInput() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.substitute(null, DITTO_HEADERS));
    }

    @Test
    public void substituteFailsWithNullHeaders() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.substitute("doesNotMatter", (DittoHeaders) null));
    }

    @Test
    public void substituteFailsWithNullWithDittoHeaders() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.substitute("doesNotMatter", (WithDittoHeaders) null));
    }

    @Test
    public void substituteReturnsInputWhenInputDoesNotContainReplacers() {
        final String input = "withoutReplacers";

        final String substituted = underTest.substitute(input, DITTO_HEADERS);

        assertThat(substituted).isSameAs(input);
    }

    @Test
    public void substituteReturnsReplacedWhenInputContainsPlaceholder() {
        final String substituted = underTest.substitute(REPLACER_1, DITTO_HEADERS);

        assertThat(substituted).isEqualTo(REPLACED_1);
    }

    @Test
    public void substituteReturnsReplacedWhenInputContainsLegacyPlaceholder() {
        final String substituted =
                underTest.substitute(LEGACY_REPLACER_1, DITTO_HEADERS);

        assertThat(substituted).isEqualTo(REPLACED_1);
    }

    @Test
    public void substituteThrowsUnknownPlaceholderExceptionWhenInputContainsUnknownPlaceholder() {
        assertUnknownPlaceholderExceptionIsThrown(UNKNOWN_REPLACER_KEY,
                () -> underTest.substitute(UNKNOWN_REPLACER, DITTO_HEADERS));
    }

    @Test
    public void substituteThrowsPlaceholderNotResolvableWhenUnresolvedPlaceholdersRemain() {
        final String notResolvableInput = "{{";
        assertUnresolvedPlaceholdersRemainExceptionIsThrown(notResolvableInput,
                () -> underTest.substitute(notResolvableInput, DITTO_HEADERS));
    }

    @Test
    public void substituteThrowsUnknownPlaceholderExceptionWhenInputContainsUnknownLegacyPlaceholder() {
        assertUnknownPlaceholderExceptionIsThrown(UNKNOWN_REPLACER_KEY,
                () -> underTest.substitute(UNKNOWN_LEGACY_REPLACER, DITTO_HEADERS));
    }

    /**
     * Nesting legacy-placeholders does not work, but at least if fails with an understandable exception. And
     * legacy-placeholders should not be used anyway.
     */
    @Test
    public void substituteThrowsWhenWhenInputContainsNestedLegacyPlaceholder() {
        final String nestedPlaceholder = "${" + LEGACY_REPLACER_1 + "}";

        assertUnknownPlaceholderExceptionIsThrown("${" + REPLACER_KEY_1 + "}",
                () -> underTest.substitute(nestedPlaceholder, DITTO_HEADERS));
    }


    private void assertUnknownPlaceholderExceptionIsThrown(final String expectedPlaceholderKey,
            final ThrowableAssert.ThrowingCallable throwingCallable) {
        final String expectedMessage = "The placeholder '" + expectedPlaceholderKey + "' is " +
                "unknown.";
        final String expectedDescription = "Please use one of the supported placeholders: '" +
                REPLACER_KEY_1 + "', '" + REPLACER_KEY_2 + "'.";
        assertPlaceholderNotResolvableExceptionIsThrown(expectedMessage, expectedDescription, throwingCallable);
    }

    private void assertUnresolvedPlaceholdersRemainExceptionIsThrown(final String notResolvableInput,
            final ThrowableAssert.ThrowingCallable throwingCallable) {
        final String expectedMessage = "The input contains not resolvable placeholders: '" + notResolvableInput + "'.";
        assertPlaceholderNotResolvableExceptionIsThrown(expectedMessage,
                GatewayPlaceholderNotResolvableException.NOT_RESOLVABLE_DESCRIPTION, throwingCallable);
    }

    private void assertPlaceholderNotResolvableExceptionIsThrown(final String expectedMessage,
            final String expectedDescription, final ThrowableAssert.ThrowingCallable throwingCallable) {
        assertThatExceptionOfType(GatewayPlaceholderNotResolvableException.class)
                .isThrownBy(throwingCallable)
                .satisfies(e -> {
                    assertThat(e.getMessage()).isEqualTo(expectedMessage);
                    assertThat(e.getDescription()).contains(expectedDescription);
                    assertThat(e.getDittoHeaders()).isEqualTo(DITTO_HEADERS);
                });
    }

}
