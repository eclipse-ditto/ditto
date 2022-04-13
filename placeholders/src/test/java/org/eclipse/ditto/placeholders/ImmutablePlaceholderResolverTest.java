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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.mutabilitydetector.unittesting.AllowedReason;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link ImmutablePlaceholderResolver}.
 */
public class ImmutablePlaceholderResolverTest {

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(ImmutablePlaceholderResolver.class, MutabilityMatchers.areImmutable(),
                AllowedReason.provided(Placeholder.class).isAlsoImmutable(),
                AllowedReason.assumingFields("placeholderSource").areNotModifiedAndDoNotEscape());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutablePlaceholderResolver.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testPlaceholderResolvementBasedOnMap() {
        final Map<String, String> inputMap = new HashMap<>();
        inputMap.put("one", "1");
        inputMap.put("two", "2");

        final ImmutablePlaceholderResolver<Map<String, String>> underTest = new ImmutablePlaceholderResolver<>(
                PlaceholderFactory.newHeadersPlaceholder(), Collections.singletonList(inputMap));

        assertThat(underTest.resolveValues("one"))
                .containsExactly("1");
        assertThat(underTest.resolveValues("two"))
                .containsExactly("2");
    }

}
