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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.eclipse.ditto.model.connectivity.UnresolvedPlaceholderException;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.junit.Test;

/**
 * Tests {@link PlaceholderFilter}.
 */
public class PlaceholderFilterTest {

    private static final Map<String, String> HEADERS = new HashMap<>();
    private static final String DEVICE_ID = "device-12345";
    private static final String THING_ID = "eclipse:ditto";

    private static final String KNOWN_NAMESPACE = "org.eclipse.ditto.test";
    private static final String KNOWN_ID = "myThing";

    private static final String KNOWN_SUBJECT = "mySubject";
    private static final String KNOWN_SUBJECT2 = "$set.configuration/steps";
    private static final TopicPath KNOWN_TOPIC_PATH = TopicPath.newBuilder(KNOWN_NAMESPACE + ":" + KNOWN_ID)
            .twin().things().commands().modify().build();
    private static final TopicPath KNOWN_TOPIC_PATH_SUBJECT1 = TopicPath.newBuilder(KNOWN_NAMESPACE + ":" + KNOWN_ID)
            .live().things().messages().subject(KNOWN_SUBJECT).build();
    private static final TopicPath KNOWN_TOPIC_PATH_SUBJECT2 = TopicPath.newBuilder(KNOWN_NAMESPACE + ":" + KNOWN_ID)
            .live().things().messages().subject(KNOWN_SUBJECT2).build();

    private static final HeadersPlaceholder headersPlaceholder = PlaceholderFactory.newHeadersPlaceholder();
    private static final ThingPlaceholder thingPlaceholder = PlaceholderFactory.newThingPlaceholder();
    private static final TopicPathPlaceholder topicPlaceholder = PlaceholderFactory.newTopicPathPlaceholder();

    private static final FilterTuple[] filterChain = new FilterTuple[]{
            FilterTuple.of(HEADERS, headersPlaceholder),
            FilterTuple.of(THING_ID, thingPlaceholder)
    };
    private static final Placeholder[] placeholders = new Placeholder[]{
            headersPlaceholder,
            thingPlaceholder
    };

    static {
        HEADERS.put("device-id", DEVICE_ID);
        HEADERS.put("gateway-id", "http-protocol-adapter");
        HEADERS.put("source", "commands");
    }

    @Test
    public void testHeadersPlaceholder() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> headersPlaceholder.resolve(HEADERS, null));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> headersPlaceholder.resolve(HEADERS, ""));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> PlaceholderFilter.apply("{{ header:unknown }}", HEADERS, headersPlaceholder));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> PlaceholderFilter.apply("{{ {{  header:device-id  }} }}", HEADERS, headersPlaceholder));
        assertThat(PlaceholderFilter.apply(THING_ID, HEADERS, headersPlaceholder)).isEqualTo(THING_ID);
        assertThat(
                PlaceholderFilter.apply("eclipse:ditto:{{ header:device-id }}", HEADERS, headersPlaceholder)).isEqualTo(
                "eclipse:ditto:device-12345");
    }

    @Test
    public void testThingPlaceholder() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> thingPlaceholder.resolve(THING_ID, null));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> thingPlaceholder.resolve(THING_ID, ""));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> PlaceholderFilter.apply("{{ header:unknown }}", THING_ID, thingPlaceholder));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> PlaceholderFilter.apply("{{ {{  thing:name  }} }}", THING_ID, thingPlaceholder));
        assertThat(PlaceholderFilter.apply(THING_ID, THING_ID, thingPlaceholder)).isEqualTo(THING_ID);
        assertThat(PlaceholderFilter.apply("prefix:{{ thing:namespace }}:{{ thing:name }}:suffix", THING_ID,
                thingPlaceholder)).isEqualTo("prefix:eclipse:ditto:suffix");
        assertThat(PlaceholderFilter.apply("testTargetAmqpCon4_{{thing:namespace}}:{{thing:name}}", THING_ID,
                thingPlaceholder))
                .isEqualTo("testTargetAmqpCon4_eclipse:ditto");

        assertThat(PlaceholderFilter.apply("testTargetAmqpCon4_{{thing:id}}", THING_ID, thingPlaceholder)).isEqualTo(
                "testTargetAmqpCon4_eclipse:ditto");
    }

    @Test
    public void testTopicPlaceholder() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> topicPlaceholder.resolve(KNOWN_TOPIC_PATH, null));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> topicPlaceholder.resolve(KNOWN_TOPIC_PATH, ""));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> PlaceholderFilter.apply("{{ topic:unknown }}", KNOWN_TOPIC_PATH, topicPlaceholder));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> PlaceholderFilter.apply("{{ {{  topic:name  }} }}", KNOWN_TOPIC_PATH, topicPlaceholder));
        assertThat(PlaceholderFilter.apply("eclipse:ditto", KNOWN_TOPIC_PATH, topicPlaceholder)).isEqualTo("eclipse:ditto");
        assertThat(PlaceholderFilter.apply("prefix:{{ topic:channel }}:{{ topic:group }}:suffix", KNOWN_TOPIC_PATH,
                topicPlaceholder)).isEqualTo("prefix:twin:things:suffix");

        assertThat(PlaceholderFilter.apply("{{topic:subject}}", KNOWN_TOPIC_PATH_SUBJECT1,
                topicPlaceholder)).isEqualTo(KNOWN_SUBJECT);
        assertThat(PlaceholderFilter.apply("{{  topic:action-subject}}", KNOWN_TOPIC_PATH_SUBJECT2,
                topicPlaceholder)).isEqualTo(KNOWN_SUBJECT2);
    }

    @Test
    public void testThingPlaceholderDebug() {
        assertThat(PlaceholderFilter.apply("testTargetAmqpCon4_{{thing:namespace}}:{{thing:name}}", THING_ID,
                thingPlaceholder))
                .isEqualTo("testTargetAmqpCon4_eclipse:ditto");
    }

    @Test
    public void testValidPlaceholderVariations() {

        // no whitespace
        assertThat(filterChain("{{thing:namespace}}/{{thing:name}}:{{header:device-id}}",
                filterChain)).isEqualTo("eclipse/ditto:" + DEVICE_ID);

        // multi whitespace
        assertThat(filterChain("{{  thing:namespace  }}/{{  thing:name  }}:{{  header:device-id  }}",
                filterChain)).isEqualTo("eclipse/ditto:" + DEVICE_ID);

        // mixed whitespace
        assertThat(filterChain("{{thing:namespace }}/{{  thing:name }}:{{header:device-id }}",
                filterChain)).isEqualTo("eclipse/ditto:" + DEVICE_ID);

        // no separators
        assertThat(filterChain("{{thing:namespace }}{{  thing:name }}{{header:device-id }}",
                filterChain)).isEqualTo("eclipseditto" + DEVICE_ID);

        // whitespace separators
        assertThat(filterChain("{{thing:namespace }}  {{  thing:name }}  {{header:device-id }}",
                filterChain)).isEqualTo("eclipse  ditto  " + DEVICE_ID);

        // pre/postfix whitespace
        assertThat(filterChain("  {{thing:namespace }}{{  thing:name }}{{header:device-id }}  ",
                filterChain)).isEqualTo("  eclipseditto" + DEVICE_ID + "  ");

        // pre/postfix
        assertThat(filterChain("-----{{thing:namespace }}{{  thing:name }}{{header:device-id }}-----",
                filterChain)).isEqualTo("-----eclipseditto" + DEVICE_ID + "-----");

        // pre/postfix and separators
        assertThat(filterChain("-----{{thing:namespace }}///{{  thing:name }}///{{header:device-id }}-----",
                filterChain)).isEqualTo("-----eclipse///ditto///" + DEVICE_ID + "-----");
    }

    @Test
    public void testInvalidPlaceholderVariations() {

        // illegal braces combinations
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> filterChain("{{th{{ing:namespace }}{{  thing:name }}{{header:device-id }}",
                        filterChain));

        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> filterChain("{{th}}ing:namespace }}{{  thing:name }}{{header:device-id }}",
                        filterChain));

        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> filterChain("{{thing:nam{{espace }}{{  thing:name }}{{header:device-id }}",
                        filterChain));

        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> filterChain("{{thing:nam}}espace }}{{  thing:name }}{{header:device-id }}",
                        filterChain));
    }

    @Test
    public void testValidate() {
        // no whitespace
        PlaceholderFilter.validate("{{thing:namespace}}/{{thing:name}}:{{header:device-id}}", placeholders);

        // multi whitespace
        PlaceholderFilter.validate("{{  thing:namespace  }}/{{  thing:name  }}:{{  header:device-id  }}", placeholders);

        // mixed whitespace
        PlaceholderFilter.validate("{{thing:namespace }}/{{  thing:name }}:{{header:device-id }}", placeholders);

        // no separators
        PlaceholderFilter.validate("{{thing:namespace }}{{  thing:name }}{{header:device-id }}", placeholders);

        // whitespace separators
        PlaceholderFilter.validate("{{thing:namespace }}  {{  thing:name }}  {{header:device-id }}", placeholders);

        // pre/postfix whitespace
        PlaceholderFilter.validate("  {{thing:namespace }}{{  thing:name }}{{header:device-id }}  ", placeholders);

        // pre/postfix
        PlaceholderFilter.validate("-----{{thing:namespace }}{{  thing:name }}{{header:device-id }}-----", placeholders);

        // pre/postfix and separators
        PlaceholderFilter.validate("-----{{thing:namespace }}///{{  thing:name }}///{{header:device-id }}-----", placeholders);
    }

    @Test
    public void testValidateFails() {
        // illegal braces combinations
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> PlaceholderFilter.validate("{{th{{ing:namespace }}{{  thing:name }}{{header:device-id }}", placeholders));

        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> PlaceholderFilter.validate("{{th}}ing:namespace }}{{  thing:name }}{{header:device-id }}", placeholders));

        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> PlaceholderFilter.validate("{{thing:nam{{espace }}{{  thing:name }}{{header:device-id }}", placeholders));

        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> PlaceholderFilter.validate("{{thing:nam}}espace }}{{  thing:name }}{{header:device-id }}", placeholders));

        assertThatExceptionOfType(PlaceholderFunctionTooComplexException.class).isThrownBy(
                () -> PlaceholderFilter.validate("{{ header:unknown | fn:default('fallback') | fn:upper() | fn:lower() | fn:upper() | fn:lower() | fn:upper() | fn:lower() | fn:upper() | fn:lower() | fn:upper() | fn:lower() }}", placeholders));
    }

    @Test
    public void testValidateAndReplace() {
        final String replacement = UUID.randomUUID().toString();
        // no whitespace
        assertThat(PlaceholderFilter.validateAndReplace("{{thing:namespace}}/{{thing:name}}:{{header:device-id}}", replacement, placeholders))
            .isEqualTo(String.format("%s/%s:%s", replacement, replacement, replacement));

        // multi whitespace
        assertThat(PlaceholderFilter.validateAndReplace("{{  thing:namespace  }}/{{  thing:name  }}:{{  header:device-id  }}", replacement, placeholders))
            .isEqualTo(String.format("%s/%s:%s", replacement, replacement, replacement));

        // mixed whitespace
        assertThat(PlaceholderFilter.validateAndReplace("{{thing:namespace }}/{{  thing:name }}:{{header:device-id }}", replacement, placeholders))
            .isEqualTo(String.format("%s/%s:%s", replacement, replacement, replacement));

        // no separators
        assertThat(PlaceholderFilter.validateAndReplace("{{thing:namespace }}{{  thing:name }}{{header:device-id }}", replacement, placeholders))
            .isEqualTo(String.format("%s%s%s", replacement, replacement, replacement));

        // whitespace separators
        assertThat(PlaceholderFilter.validateAndReplace("{{thing:namespace }}  {{  thing:name }}  {{header:device-id }}", replacement, placeholders))
            .isEqualTo(String.format("%s  %s  %s", replacement, replacement, replacement));

        // pre/postfix whitespace
        assertThat(PlaceholderFilter.validateAndReplace("  {{thing:namespace }}{{  thing:name }}{{header:device-id }}  ", replacement, placeholders))
            .isEqualTo(String.format("  %s%s%s  ", replacement, replacement, replacement));

        // pre/postfix
        assertThat(PlaceholderFilter.validateAndReplace("-----{{thing:namespace }}{{  thing:name }}{{header:device-id }}-----", replacement, placeholders))
            .isEqualTo(String.format("-----%s%s%s-----", replacement, replacement, replacement));

        // pre/postfix and separators
        assertThat(PlaceholderFilter.validateAndReplace("-----{{thing:namespace }}///{{  thing:name }}///{{header:device-id }}-----", replacement, placeholders))
            .isEqualTo(String.format("-----%s///%s///%s-----", replacement, replacement, replacement));
    }

    private static String filterChain(final String template, final FilterTuple... tuples) {
        String result = template;
        for (final FilterTuple tuple : tuples) {
            result = PlaceholderFilter.apply(result, tuple.value, tuple.placeholder, true);
        }
        return PlaceholderFilter.checkAllPlaceholdersResolved(result);
    }

    static class FilterTuple {

        final Object value;
        final Placeholder placeholder;

        private FilterTuple(final Object value, final Placeholder placeholder) {
            this.value = value;
            this.placeholder = placeholder;
        }

        static FilterTuple of(final Object value, final Placeholder placeholder) {
            return new FilterTuple(value, placeholder);
        }
    }
}
