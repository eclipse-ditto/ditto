/*
 *  Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 *  SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.ditto.services.models.connectivity.placeholder;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.connectivity.UnresolvedPlaceholderException;
import org.junit.Test;

/**
 * Tests {@link PlaceholderFilter}.
 */
public class PlaceholderFilterTest {

    private static final Map<String, String> HEADERS = new HashMap<>();
    private static final String SOLUTION_ID = "solution-id";
    private static final String DEVICE_ID = "device-12345";
    private static final String THING_ID = "eclipse:ditto";

    private final HeadersPlaceholder headersPlaceholder = ImmutableHeadersPlaceholder.INSTANCE;
    private final ThingPlaceholder thingPlaceholder = ImmutableThingPlaceholder.INSTANCE;
    private final PlaceholderFilter underTest = new PlaceholderFilter();

    private final FilterTuple[] filterChain = new FilterTuple[]{
            FilterTuple.of(HEADERS, headersPlaceholder),
            FilterTuple.of(THING_ID, thingPlaceholder)
    };

    static {
        HEADERS.put("device-id", DEVICE_ID);
        HEADERS.put("gateway-id", "http-protocol-adapter");
        HEADERS.put("source", "commands");
    }

    @Test
    public void testHeadersPlaceholder() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> headersPlaceholder.apply(HEADERS, null));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> headersPlaceholder.apply(HEADERS, ""));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> underTest.apply("{{ header:unknown }}", HEADERS, headersPlaceholder));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> underTest.apply("{{}}", HEADERS, headersPlaceholder));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> underTest.apply("{{ {{  header:device-id  }} }}", HEADERS, headersPlaceholder));
        assertThat(underTest.apply("eclipse:ditto", HEADERS, headersPlaceholder)).isEqualTo("eclipse:ditto");
        assertThat(underTest.apply("eclipse:ditto:{{ header:device-id }}", HEADERS, headersPlaceholder)).isEqualTo(
                "eclipse:ditto:device-12345");
    }

    @Test
    public void testThingPlaceholder() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> thingPlaceholder.apply(THING_ID, null));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> thingPlaceholder.apply(THING_ID, ""));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> underTest.apply("{{ header:unknown }}", THING_ID, thingPlaceholder));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> underTest.apply("{{}}", THING_ID, thingPlaceholder));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> underTest.apply("{{ {{  thing:name  }} }}", THING_ID, thingPlaceholder));
        assertThat(underTest.apply("eclipse:ditto", THING_ID, thingPlaceholder)).isEqualTo("eclipse:ditto");
        assertThat(underTest.apply("prefix:{{ thing:namespace }}:{{ thing:name }}:suffix", THING_ID,
                thingPlaceholder)).isEqualTo("prefix:eclipse:ditto:suffix");
        assertThat(underTest.apply("testTargetAmqpCon4_{{thing:namespace}}:{{thing:name}}", THING_ID, thingPlaceholder))
                .isEqualTo("testTargetAmqpCon4_eclipse:ditto");

        assertThat(underTest.apply("testTargetAmqpCon4_{{thing:id}}", THING_ID, thingPlaceholder)).isEqualTo(
                "testTargetAmqpCon4_eclipse:ditto");
    }

    @Test
    public void testThingPlaceholderDebug() {
        assertThat(underTest.apply("testTargetAmqpCon4_{{thing:namespace}}:{{thing:name}}", THING_ID, thingPlaceholder))
                .isEqualTo("testTargetAmqpCon4_eclipse:ditto");
    }

    @Test
    public void testValidPlaceholderVariations() {

        // no whitespace
        assertThat(filterChain(underTest, "{{thing:namespace}}/{{thing:name}}:{{header:device-id}}",
                filterChain)).isEqualTo("eclipse/ditto:" + DEVICE_ID);

        // multi whitespace
        assertThat(filterChain(underTest, "{{  thing:namespace  }}/{{  thing:name  }}:{{  header:device-id  }}",
                filterChain)).isEqualTo("eclipse/ditto:" + DEVICE_ID);

        // mixed whitespace
        assertThat(filterChain(underTest, "{{thing:namespace }}/{{  thing:name }}:{{header:device-id }}",
                filterChain)).isEqualTo("eclipse/ditto:" + DEVICE_ID);

        // no separators
        assertThat(filterChain(underTest, "{{thing:namespace }}{{  thing:name }}{{header:device-id }}",
                filterChain)).isEqualTo("eclipseditto" + DEVICE_ID);

        // whitespace separators
        assertThat(filterChain(underTest, "{{thing:namespace }}  {{  thing:name }}  {{header:device-id }}",
                filterChain)).isEqualTo("eclipse  ditto  " + DEVICE_ID);

        // pre/postfix whitespace
        assertThat(filterChain(underTest, "  {{thing:namespace }}{{  thing:name }}{{header:device-id }}  ",
                filterChain)).isEqualTo("  eclipseditto" + DEVICE_ID + "  ");

        // pre/postfix
        assertThat(filterChain(underTest, "-----{{thing:namespace }}{{  thing:name }}{{header:device-id }}-----",
                filterChain)).isEqualTo("-----eclipseditto" + DEVICE_ID + "-----");

        // pre/postfix and separators
        assertThat(filterChain(underTest, "-----{{thing:namespace }}///{{  thing:name }}///{{header:device-id }}-----",
                filterChain)).isEqualTo("-----eclipse///ditto///" + DEVICE_ID + "-----");
    }

    @Test
    public void testInvalidPlaceholderVariations() {

        // illegal braces combinations
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> filterChain(underTest, "{{th{{ing:namespace }}{{  thing:name }}{{header:device-id }}",
                        filterChain));

        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> filterChain(underTest, "{{th}}ing:namespace }}{{  thing:name }}{{header:device-id }}",
                        filterChain));

        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> filterChain(underTest, "{{thing:nam{{espace }}{{  thing:name }}{{header:device-id }}",
                        filterChain));

        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> filterChain(underTest, "{{thing:nam}}espace }}{{  thing:name }}{{header:device-id }}",
                        filterChain));
    }

    private static String filterChain(final PlaceholderFilter filter, final String template,
            final FilterTuple... tuples) {
        String result = template;
        for (final FilterTuple tuple : tuples) {
            result = filter.apply(result, tuple.value, tuple.placeholder, false);
        }
        return PlaceholderFilter.checkAllPlaceholdersResolved(result);
    }

    static class FilterTuple {

        Object value;
        Placeholder<Object> placeholder;

        private FilterTuple(final Object value, final Placeholder<Object> placeholder) {
            this.value = value;
            this.placeholder = placeholder;
        }

        static <T> FilterTuple of(final T value, final Placeholder<T> placeholder) {
            return new FilterTuple(value, (Placeholder<Object>) placeholder);
        }
    }
}
