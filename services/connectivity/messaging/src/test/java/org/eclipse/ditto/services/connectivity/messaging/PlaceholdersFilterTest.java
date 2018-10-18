/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */

package org.eclipse.ditto.services.connectivity.messaging;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.connectivity.UnresolvedPlaceholderException;
import org.junit.Test;

/**
 * Tests {@link PlaceholderFilter}.
 */
public class PlaceholdersFilterTest {

    private static final Map<String, String> HEADERS = new HashMap<>();
    private static final String DEVICE_ID = "device-12345";

    private final PlaceholderFilter.Placeholder headersPlaceholder = PlaceholderFilter.headers(HEADERS);
    private final PlaceholderFilter.Placeholder thingPlaceholder = PlaceholderFilter.thing("eclipse:ditto");
    private final PlaceholderFilter underTest = new PlaceholderFilter();


    static {
        HEADERS.put("device-id", DEVICE_ID);
        HEADERS.put("gateway-id", "http-protocol-adapter");
        HEADERS.put("source", "commands");
    }

    @Test
    public void testHeadersPlaceholder() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> headersPlaceholder.apply(null));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> headersPlaceholder.apply(""));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> underTest.apply("{{ header:unknown }}", headersPlaceholder));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> underTest.apply("{{}}", headersPlaceholder));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> underTest.apply("{{ {{  header:device-id  }} }}", headersPlaceholder));
        assertThat(underTest.apply("eclipse:ditto", headersPlaceholder)).isEqualTo("eclipse:ditto");
        assertThat(underTest.apply("eclipse:ditto:{{ header:device-id }}", headersPlaceholder)).isEqualTo(
                "eclipse:ditto:device-12345");
    }

    @Test
    public void testThingPlaceholder() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> thingPlaceholder.apply(null));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> thingPlaceholder.apply(""));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> underTest.apply("{{ header:unknown }}", thingPlaceholder));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> underTest.apply("{{}}", thingPlaceholder));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> underTest.apply("{{ {{  thing:name  }} }}", thingPlaceholder));
        assertThat(underTest.apply("eclipse:ditto", thingPlaceholder)).isEqualTo("eclipse:ditto");
        assertThat(underTest.apply("prefix:{{ thing:namespace }}:{{ thing:name }}:suffix", thingPlaceholder)).isEqualTo(
                "prefix:eclipse:ditto:suffix");
        assertThat(underTest.apply("testTargetAmqpCon4_{{thing:namespace}}:{{thing:name}}", thingPlaceholder))
                .isEqualTo(
                        "testTargetAmqpCon4_eclipse:ditto");

        assertThat(underTest.apply("testTargetAmqpCon4_{{thing:id}}", thingPlaceholder))
                .isEqualTo("testTargetAmqpCon4_eclipse:ditto");
    }

    @Test
    public void testMultiplePlaceholders() {
        final String template = "{{thing:namespace }}/{{ thing:name }}:{{header:device-id }}";
        final String expected = "eclipse/ditto:" + DEVICE_ID;
        assertThat(underTest.apply(template, headersPlaceholder, thingPlaceholder)).isEqualTo(expected);
    }

    @Test
    public void testValidPlaceholderVariations() {

        // no whitespace
        assertThat(underTest.apply("{{thing:namespace}}/{{thing:name}}:{{header:device-id}}",
                headersPlaceholder, thingPlaceholder)).isEqualTo("eclipse/ditto:" + DEVICE_ID);

        // multi whitespace
        assertThat(underTest.apply("{{  thing:namespace  }}/{{  thing:name  }}:{{  header:device-id  }}",
                headersPlaceholder, thingPlaceholder)).isEqualTo("eclipse/ditto:" + DEVICE_ID);

        // mixed whitespace
        assertThat(underTest.apply("{{thing:namespace }}/{{  thing:name }}:{{header:device-id }}",
                headersPlaceholder, thingPlaceholder)).isEqualTo("eclipse/ditto:" + DEVICE_ID);

        // no separators
        assertThat(underTest.apply("{{thing:namespace }}{{  thing:name }}{{header:device-id }}",
                headersPlaceholder, thingPlaceholder)).isEqualTo("eclipseditto" + DEVICE_ID);

        // whitespace separators
        assertThat(underTest.apply("{{thing:namespace }}  {{  thing:name }}  {{header:device-id }}",
                headersPlaceholder, thingPlaceholder)).isEqualTo("eclipse  ditto  " + DEVICE_ID);

        // pre/postfix whitespace
        assertThat(underTest.apply("  {{thing:namespace }}{{  thing:name }}{{header:device-id }}  ",
                headersPlaceholder, thingPlaceholder)).isEqualTo("  eclipseditto" + DEVICE_ID + "  ");

        // pre/postfix
        assertThat(underTest.apply("-----{{thing:namespace }}{{  thing:name }}{{header:device-id }}-----",
                headersPlaceholder, thingPlaceholder)).isEqualTo("-----eclipseditto" + DEVICE_ID + "-----");

        // pre/postfix and separators
        assertThat(underTest.apply("-----{{thing:namespace }}///{{  thing:name }}///{{header:device-id }}-----",
                headersPlaceholder, thingPlaceholder)).isEqualTo("-----eclipse///ditto///" + DEVICE_ID + "-----");
    }

    @Test
    public void testInvalidPlaceholderVariations() {

        // illegal braces combinations
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> underTest.apply("{{th{{ing:namespace }}{{  thing:name }}{{header:device-id }}",
                        headersPlaceholder, thingPlaceholder));

        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> underTest.apply("{{th}}ing:namespace }}{{  thing:name }}{{header:device-id }}",
                        headersPlaceholder, thingPlaceholder));

        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> underTest.apply("{{thing:nam{{espace }}{{  thing:name }}{{header:device-id }}",
                        headersPlaceholder, thingPlaceholder));

        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> underTest.apply("{{thing:nam}}espace }}{{  thing:name }}{{header:device-id }}",
                        headersPlaceholder, thingPlaceholder));
    }
}
