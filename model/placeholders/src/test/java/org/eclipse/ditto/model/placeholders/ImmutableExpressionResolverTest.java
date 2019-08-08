/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.placeholders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Arrays;
import java.util.Map;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.UnresolvedPlaceholderException;
import org.eclipse.ditto.model.things.id.ThingId;
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
    private static final String THING_NAMESPACE = "org.eclipse.ditto";
    private static final ThingId THING_ID = ThingId.of(THING_NAMESPACE, THING_NAME);
    private static final String KNOWN_TOPIC = "org.eclipse.ditto/" + THING_NAME + "/things/twin/commands/modify";
    private static final Map<String, String> KNOWN_HEADERS =
            DittoHeaders.newBuilder().putHeader("one", "1").putHeader("two", "2").build();
    private static final String UNKNOWN_HEADER_EXPRESSION = "{{ header:missing }}";
    private static final String UNKNOWN_THING_EXPRESSION = "{{ thing:missing }}";
    private static final String UNKNOWN_TOPIC_EXPRESSION = "{{ topic:missing }}";

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
        final TopicPath topic = ProtocolFactory.newTopicPath(KNOWN_TOPIC);

        final ImmutablePlaceholderResolver<Map<String, String>> headersResolver =
                new ImmutablePlaceholderResolver<>(PlaceholderFactory.newHeadersPlaceholder(), KNOWN_HEADERS, false);
        final ImmutablePlaceholderResolver<CharSequence> thingResolver = new ImmutablePlaceholderResolver<>(
                PlaceholderFactory.newThingPlaceholder(), THING_ID, false);
        final ImmutablePlaceholderResolver<TopicPath> topicPathResolver = new ImmutablePlaceholderResolver<>(
                PlaceholderFactory.newTopicPathPlaceholder(), topic, false);

        underTest = new ImmutableExpressionResolver(Arrays.asList(headersResolver, thingResolver, topicPathResolver));
    }

    @Test
    public void testSuccessfulPlaceholderResolution() {

        assertThat(underTest.resolve("{{ header:one }}", false))
                .isEqualTo(KNOWN_HEADERS.get("one"));
        assertThat(underTest.resolve("{{ header:two }}", false))
                .isEqualTo(KNOWN_HEADERS.get("two"));
        assertThat(underTest.resolve("{{ thing:id }}", false))
                .isEqualTo(THING_ID.toString());
        assertThat(underTest.resolve("{{ thing:name }}", false))
                .isEqualTo(THING_NAME);
        assertThat(underTest.resolve("{{ topic:full }}", false))
                .isEqualTo(KNOWN_TOPIC);
        assertThat(underTest.resolve("{{ topic:entityId }}", false))
                .isEqualTo(THING_NAME);

        // verify different whitespace
        assertThat(underTest.resolve("{{topic:entityId }}", false))
                .isEqualTo(THING_NAME);
        assertThat(underTest.resolve("{{topic:entityId}}", false))
                .isEqualTo(THING_NAME);
        assertThat(underTest.resolve("{{        topic:entityId}}", false))
                .isEqualTo(THING_NAME);
    }

    @Test
    public void testPlaceholderResolutionAllowingUnresolvedPlaceholders() {

        assertThat(underTest.resolve(UNKNOWN_HEADER_EXPRESSION, true))
                .isEqualTo(UNKNOWN_HEADER_EXPRESSION);
        assertThat(underTest.resolve(UNKNOWN_THING_EXPRESSION, true))
                .isEqualTo(UNKNOWN_THING_EXPRESSION);
        assertThat(underTest.resolve(UNKNOWN_TOPIC_EXPRESSION, true))
                .isEqualTo(UNKNOWN_TOPIC_EXPRESSION);
    }

    @Test
    public void testUnsuccessfulPlaceholderResolution() {

        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(() ->
                underTest.resolve(UNKNOWN_HEADER_EXPRESSION, false));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(() ->
                underTest.resolve(UNKNOWN_THING_EXPRESSION, false));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(() ->
                underTest.resolve(UNKNOWN_TOPIC_EXPRESSION, false));
    }

    @Test
    public void testSuccessfulFunctionBasedOnPlaceholderInput() {

        assertThat(underTest.resolve("{{ header:unknown | fn:default('fallback') }}", false))
                .isEqualTo("fallback");
        assertThat(underTest.resolve("{{ thing:bar | fn:default('bar') | fn:upper() }}", false))
                .isEqualTo("BAR");
        assertThat(underTest.resolve("{{ thing:id | fn:substring-before(':') }}", false))
                .isEqualTo(THING_NAMESPACE);
        assertThat(underTest.resolve("{{ thing:id | fn:substring-before(':') | fn:default('foo') }}", false))
                .isEqualTo(THING_NAMESPACE);
        assertThat(underTest.resolve("any/prefix/{{ thing:id | fn:substring-before(':') | fn:default('foo') }}", false))
                .isEqualTo("any/prefix/" + THING_NAMESPACE);
        assertThat(underTest.resolve("{{ header:unknown | fn:default(' fallback-spaces  ') }}", false))
                .isEqualTo(" fallback-spaces  ");

        // verify different whitespace
        assertThat(underTest.resolve("{{thing:bar |fn:default('bar')| fn:upper() }}", false))
                .isEqualTo("BAR");
        assertThat(underTest.resolve("{{    thing:bar |     fn:default('bar')    |fn:upper()}}", false))
                .isEqualTo("BAR");
        assertThat(underTest.resolve("{{ thing:bar | fn:default(  'bar'  ) |fn:upper(  ) }}", false))
                .isEqualTo("BAR");
        assertThat(underTest.resolve(
                "{{ thing:id | fn:substring-before(\"|\") | fn:default('bAz') | fn:lower() }}",
                false))
                .isEqualTo("baz");
    }

    @Test
    public void testSpecialCharactersInStrings() {
        assertThat(underTest.resolve("{{ thing:bar | fn:default( ' \\s%!@/*+\"\\'上手カキクケコ' ) | fn:upper( ) }}", false))
                .isEqualTo(" \\S%!@/*+\"\\'上手カキクケコ");

        assertThat(underTest.resolve("{{ thing:bar | fn:default( \" \\s%!@/*+'\\\"上手カキクケコ\" ) | fn:upper( ) }}", false))
                .isEqualTo(" \\S%!@/*+'\\\"上手カキクケコ");
    }

    @Test
    public void rejectUnsupportedPlaceholdersWithSpecialCharacters() {
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(() ->
                underTest.resolve("{{ thing:id\\s%!@/*+上手カキクケコ }}", false));
    }

    @Test
    public void testUnsuccessfulFunctionBasedOnPlaceholderInput() {

        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(() ->
                underTest.resolve("{{ header:unknown }}", false));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(() ->
                underTest.resolve("{{ thing:bar | fn:upper() }}", false));
    }

    @Test
    public void testSuccessfulSinglePlaceholderResolution() {
        assertThat(underTest.resolveSinglePlaceholder("header:one"))
                .contains(KNOWN_HEADERS.get("one"));
        assertThat(underTest.resolveSinglePlaceholder("thing:id"))
                .contains(THING_ID.toString());
    }

    @Test
    public void testUnsuccessfulSinglePlaceholderResolution() {
        assertThat(underTest.resolveSinglePlaceholder("header:unknown"))
                .isEmpty();
        assertThat(underTest.resolveSinglePlaceholder("fn:default('fallback')"))
                .isEmpty();
        assertThat(underTest.resolveSinglePlaceholder("fn:substring-before()"))
                .isEmpty();
    }

    @Test
    public void testResolveBarelyTooComplexFunctionChainWorks() {
        // 10 functions should work:
        assertThat(underTest.resolve(
                "{{ header:unknown | fn:default('fallback') | fn:upper() | fn:lower() | fn:upper() | fn:lower() | fn:upper() | fn:lower() | fn:upper() | fn:lower() | fn:upper() }}",
                false))
                .isEqualTo("FALLBACK");
    }

    @Test
    public void testResolveTooComplexFunctionChainResultsInException() {
        // 11 functions should fail:
        assertThatExceptionOfType(PlaceholderFunctionTooComplexException.class).isThrownBy(() ->
                underTest.resolve(
                        "{{ header:unknown | fn:default('fallback') | fn:upper() | fn:lower() | fn:upper() | fn:lower() | fn:upper() | fn:lower() | fn:upper() | fn:lower() | fn:upper() | fn:lower() }}",
                        false));
    }
}
