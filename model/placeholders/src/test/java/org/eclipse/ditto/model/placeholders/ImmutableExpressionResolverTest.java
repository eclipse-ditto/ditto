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
package org.eclipse.ditto.model.placeholders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.connectivity.UnresolvedPlaceholderException;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
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

    private static final String THING_NAME = "foobar199";
    private static final String THING_ID = "org.eclipse.ditto:" + THING_NAME;
    private static final String KNOWN_TOPIC = "org.eclipse.ditto/" + THING_NAME + "/things/twin/commands/modify";

    private static ImmutableExpressionResolver underTest;

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(ImmutableExpressionResolver.class, MutabilityMatchers.areImmutable(),
                AllowedReason.assumingFields("placeholderResolvers")
                        .areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableExpressionResolver.class)
                .usingGetClass()
                .verify();
    }

    @BeforeClass
    public static void setupClass() {
        final Map<String, String> inputMap = new HashMap<>();
        inputMap.put("one", "1");
        inputMap.put("two", "2");

        final TopicPath topic = ProtocolFactory.newTopicPath(KNOWN_TOPIC);

        final ImmutablePlaceholderResolver<Map<String, String>> placeholderResolver1 =
                new ImmutablePlaceholderResolver<>(
                        PlaceholderFactory.newHeadersPlaceholder(), inputMap, false);
        final ImmutablePlaceholderResolver<String> placeholderResolver2 = new ImmutablePlaceholderResolver<>(
                PlaceholderFactory.newThingPlaceholder(), THING_ID, false);
        final ImmutablePlaceholderResolver<TopicPath> placeholderResolver3 = new ImmutablePlaceholderResolver<>(
                PlaceholderFactory.newTopicPathPlaceholder(), topic, false);

        underTest = new ImmutableExpressionResolver(
                Arrays.asList(placeholderResolver1, placeholderResolver2, placeholderResolver3));
    }

    @Test
    public void testSuccessfulPlaceholderResolvement() {

        assertThat(underTest.resolve("{{ header:one }}", false))
                .contains("1");
        assertThat(underTest.resolve("{{ header:two }}", false))
                .contains("2");
        assertThat(underTest.resolve("{{ thing:id }}", false))
                .contains(THING_ID);
        assertThat(underTest.resolve("{{ thing:name }}", false))
                .contains(THING_NAME);
        assertThat(underTest.resolve("{{ topic:full }}", false))
                .contains(KNOWN_TOPIC);
        assertThat(underTest.resolve("{{ topic:entityId }}", false))
                .contains(THING_NAME);
    }

    @Test
    public void testPlaceholderResolvementAllowingUnresolvedPlaceholders() {

        assertThat(underTest.resolve("{{ header:missing }}", true))
                .contains("{{ header:missing }}");
        assertThat(underTest.resolve("{{ thing:missing }}", true))
                .contains("{{ thing:missing }}");
        assertThat(underTest.resolve("{{ topic:missing }}", true))
                .contains("{{ topic:missing }}");
    }

    @Test
    public void testUnsuccessfulPlaceholderResolvement() {

        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(() ->
                underTest.resolve("{{ header:missing }}", false));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(() ->
                underTest.resolve("{{ thing:missing }}", false));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(() ->
                underTest.resolve("{{ topic:missing }}", false));
    }

    @Test
    public void testSuccessfulFunctionBasedOnPlaceholderInput() {

        assertThat(underTest.resolve("{{ header:unknown | fn:default('fallback') }}", false))
                .contains("fallback");
        assertThat(underTest.resolve("{{ thing:bar | fn:default('bar') | fn:upper() }}", false))
                .contains("BAR");
    }

    @Test
    public void testUnsuccessfulFunctionBasedOnPlaceholderInput() {

        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(() ->
                underTest.resolve("{{ header:unknown }}", false));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(() ->
                underTest.resolve("{{ thing:bar | fn:upper() }}", false));
    }

}
