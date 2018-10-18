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
    private static final String DEVICE_ID = "device-12345";
    private static final String THING_ID = "eclipse:ditto";

    private final HeadersPlaceholder headersPlaceholder = PlaceholderFactory.newHeadersPlaceholder();
    private final ThingPlaceholder thingPlaceholder = PlaceholderFactory.newThingPlaceholder();

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
                () -> PlaceholderFilter.apply("{{ header:unknown }}", HEADERS, headersPlaceholder));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> PlaceholderFilter.apply("{{ {{  header:device-id  }} }}", HEADERS, headersPlaceholder));
        assertThat(PlaceholderFilter.apply("eclipse:ditto", HEADERS, headersPlaceholder)).isEqualTo("eclipse:ditto");
        assertThat(
                PlaceholderFilter.apply("eclipse:ditto:{{ header:device-id }}", HEADERS, headersPlaceholder)).isEqualTo(
                "eclipse:ditto:device-12345");
    }

    @Test
    public void testThingPlaceholder() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> thingPlaceholder.apply(THING_ID, null));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> thingPlaceholder.apply(THING_ID, ""));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> PlaceholderFilter.apply("{{ header:unknown }}", THING_ID, thingPlaceholder));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> PlaceholderFilter.apply("{{ {{  thing:name  }} }}", THING_ID, thingPlaceholder));
        assertThat(PlaceholderFilter.apply("eclipse:ditto", THING_ID, thingPlaceholder)).isEqualTo("eclipse:ditto");
        assertThat(PlaceholderFilter.apply("prefix:{{ thing:namespace }}:{{ thing:name }}:suffix", THING_ID,
                thingPlaceholder)).isEqualTo("prefix:eclipse:ditto:suffix");
        assertThat(PlaceholderFilter.apply("testTargetAmqpCon4_{{thing:namespace}}:{{thing:name}}", THING_ID,
                thingPlaceholder))
                .isEqualTo("testTargetAmqpCon4_eclipse:ditto");

        assertThat(PlaceholderFilter.apply("testTargetAmqpCon4_{{thing:id}}", THING_ID, thingPlaceholder)).isEqualTo(
                "testTargetAmqpCon4_eclipse:ditto");
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
