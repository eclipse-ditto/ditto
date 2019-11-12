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
import org.eclipse.ditto.model.things.ThingId;
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
        final TopicPath topic = ProtocolFactory.newTopicPath(KNOWN_TOPIC);

        final ImmutablePlaceholderResolver<Map<String, String>> headersResolver =
                new ImmutablePlaceholderResolver<>(PlaceholderFactory.newHeadersPlaceholder(), KNOWN_HEADERS);
        final ImmutablePlaceholderResolver<CharSequence> thingResolver = new ImmutablePlaceholderResolver<>(
                PlaceholderFactory.newThingPlaceholder(), THING_ID);
        final ImmutablePlaceholderResolver<TopicPath> topicPathResolver = new ImmutablePlaceholderResolver<>(
                PlaceholderFactory.newTopicPathPlaceholder(), topic);

        underTest = new ImmutableExpressionResolver(Arrays.asList(headersResolver, thingResolver, topicPathResolver));
    }

    @Test
    public void testSuccessfulPlaceholderResolution() {

        assertThat(underTest.resolve("{{ header:one }}", false))
                .contains(KNOWN_HEADERS.get("one"));
        assertThat(underTest.resolve("{{ header:two }}", false))
                .contains(KNOWN_HEADERS.get("two"));
        assertThat(underTest.resolve("{{ thing:id }}", false))
                .contains(THING_ID.toString());
        assertThat(underTest.resolve("{{ thing:name }}", false))
                .contains(THING_NAME);
        assertThat(underTest.resolve("{{ topic:full }}", false))
                .contains(KNOWN_TOPIC);
        assertThat(underTest.resolve("{{ topic:entityId }}", false))
                .contains(THING_NAME);

        // verify different whitespace
        assertThat(underTest.resolve("{{topic:entityId }}", false))
                .contains(THING_NAME);
        assertThat(underTest.resolve("{{topic:entityId}}", false))
                .contains(THING_NAME);
        assertThat(underTest.resolve("{{        topic:entityId}}", false))
                .contains(THING_NAME);
    }

    @Test
    public void testPlaceholderResolutionAllowingUnresolvedPlaceholders() {
        // supported unresolved placeholders are retained
        assertThat(underTest.resolve(UNKNOWN_HEADER_EXPRESSION, true))
                .contains(UNKNOWN_HEADER_EXPRESSION);

        // unsupported placeholders cause error
        assertThatExceptionOfType(UnresolvedPlaceholderException.class)
                .isThrownBy(() -> underTest.resolve(UNKNOWN_THING_EXPRESSION, true));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class)
                .isThrownBy(() -> underTest.resolve(UNKNOWN_TOPIC_EXPRESSION, true));
    }

    @Test
    public void testUnsuccessfulPlaceholderResolution() {
        assertThat(underTest.resolve(UNKNOWN_HEADER_EXPRESSION, false)).isEmpty();
    }

    @Test
    public void testSuccessfulFunctionBasedOnPlaceholderInput() {

        assertThat(underTest.resolve("{{ header:unknown | fn:default('fallback') }}", false))
                .contains("fallback");
        assertThat(underTest.resolve("{{ header:unknown | fn:default('bar') | fn:upper() }}", false))
                .contains("BAR");
        assertThat(underTest.resolve("{{ thing:id | fn:substring-before(':') }}", false))
                .contains(THING_NAMESPACE);
        assertThat(underTest.resolve("{{ thing:id | fn:substring-before(':') | fn:default('foo') }}", false))
                .contains(THING_NAMESPACE);
        assertThat(underTest.resolve("any/prefix/{{ thing:id | fn:substring-before(':') | fn:default('foo') }}", false))
                .contains("any/prefix/" + THING_NAMESPACE);
        assertThat(underTest.resolve("{{ header:unknown | fn:default(' fallback-spaces  ') }}", false))
                .contains(" fallback-spaces  ");

        // verify different whitespace
        assertThat(underTest.resolve("{{header:unknown |fn:default('bar')| fn:upper() }}", false))
                .contains("BAR");
        assertThat(underTest.resolve("{{    header:unknown |     fn:default('bar')    |fn:upper()}}", false))
                .contains("BAR");
        assertThat(underTest.resolve("{{ header:unknown | fn:default(  'bar'  ) |fn:upper(  ) }}", false))
                .contains("BAR");
        assertThat(underTest.resolve(
                "{{ thing:id | fn:substring-before(\"|\") | fn:default('bAz') | fn:lower() }}", false))
                .contains("baz");
    }

    @Test
    public void testSpecialCharactersInStrings() {
        assertThat(underTest.resolve("{{ header:unknown | fn:default( ' \\s%!@/*+\"\\'上手カキクケコ' ) | fn:upper( ) }}", false))
                .contains(" \\S%!@/*+\"\\'上手カキクケコ");

        assertThat(underTest.resolve("{{ header:unknown | fn:default( \" \\s%!@/*+'\\\"上手カキクケコ\" ) | fn:upper( ) }}", false))
                .contains(" \\S%!@/*+'\\\"上手カキクケコ");
    }

    @Test
    public void rejectUnsupportedPlaceholdersWithSpecialCharacters() {
        assertThatExceptionOfType(UnresolvedPlaceholderException.class)
                .isThrownBy(() -> underTest.resolve("{{ thing:id\\s%!@/*+上手カキクケコ }}", false));
    }

    @Test
    public void testUnsuccessfulFunctionBasedOnPlaceholderInput() {
        assertThat(underTest.resolve("{{ header:unknown }}", false)).isEmpty();
        assertThat(underTest.resolve("{{ header:unknown | fn:default(header:unknown) }}", false)).isEmpty();
    }

    @Test
    public void testSuccessfulSinglePlaceholderResolution() {
        assertThat(underTest.resolveAsPipelineElement("header:one"))
                .contains(KNOWN_HEADERS.get("one"));
        assertThat(underTest.resolveAsPipelineElement("thing:id"))
                .contains(THING_ID.toString());
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
                        " | fn:lower() | fn:upper() | fn:lower() | fn:upper() | fn:lower() | fn:upper() }}", false))
                .contains("FALLBACK");
    }

    @Test
    public void testResolveTooComplexFunctionChainResultsInException() {
        // 11 functions should fail:
        assertThatExceptionOfType(PlaceholderFunctionTooComplexException.class).isThrownBy(() ->
                underTest.resolve(
                        "{{ header:unknown | fn:default('fallback') | fn:upper() | fn:lower()" +
                                " | fn:upper() | fn:lower() | fn:upper() | fn:lower() | fn:upper() | fn:lower()" +
                                " | fn:upper() | fn:lower() }}",
                        false));
    }
}
