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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;

/**
 * Tests {@link PlaceholderFilter}.
 */
public class PlaceholderFilterTest {

    private static final Map<String, String> HEADERS = new HashMap<>();
    private static final String DEVICE_ID = "device-12345";

    private static final HeadersPlaceholder headersPlaceholder = PlaceholderFactory.newHeadersPlaceholder();

    private static final PlaceholderResolver[] filterChain = new PlaceholderResolver[]{
            PlaceholderFactory.newPlaceholderResolver(headersPlaceholder, HEADERS)
    };
    private static final Placeholder[] placeholders = new Placeholder[]{headersPlaceholder};

    static {
        HEADERS.put("device-id", DEVICE_ID);
        HEADERS.put("gateway-id", "http-protocol-adapter");
        HEADERS.put("source", "commands");
    }

    @Test
    public void testHeadersPlaceholder() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(
                () -> headersPlaceholder.resolveValues(HEADERS, null));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> headersPlaceholder.resolveValues(HEADERS, ""));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> PlaceholderFilter.applyForAll("{{ header:unknown }}", HEADERS, headersPlaceholder));
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> PlaceholderFilter.applyForAll("{{ {{  header:device-id  }} }}", HEADERS, headersPlaceholder));
        assertThat(PlaceholderFilter.applyForAll(HEADERS.get("device-id"), HEADERS, headersPlaceholder))
                .containsExactly(DEVICE_ID);
        assertThat(
                PlaceholderFilter.applyForAll("http-protocol-adapter:commands:{{ header:device-id }}", HEADERS,
                        headersPlaceholder)).containsExactly("http-protocol-adapter:commands:device-12345");
    }

    @Test
    public void testValidPlaceholderVariations() {

        // no whitespace
        assertThat(filterChain("{{header:gateway-id}}/{{header:source}}:{{header:device-id}}",
                filterChain)).containsExactly("http-protocol-adapter/commands:" + DEVICE_ID);

        // multi whitespace
        assertThat(filterChain("{{  header:gateway-id  }}/{{  header:source  }}:{{  header:device-id  }}",
                filterChain)).containsExactly("http-protocol-adapter/commands:" + DEVICE_ID);

        // mixed whitespace
        assertThat(filterChain("{{header:gateway-id }}/{{  header:source }}:{{header:device-id }}",
                filterChain)).containsExactly("http-protocol-adapter/commands:" + DEVICE_ID);

        // no separators
        assertThat(filterChain("{{header:gateway-id }}{{  header:source }}{{header:device-id }}",
                filterChain)).containsExactly("http-protocol-adaptercommands" + DEVICE_ID);

        // whitespace separators
        assertThat(filterChain("{{header:gateway-id }}  {{  header:source }}  {{header:device-id }}",
                filterChain)).containsExactly("http-protocol-adapter  commands  " + DEVICE_ID);

        // pre/postfix whitespace
        assertThat(filterChain("  {{header:gateway-id }}{{  header:source }}{{header:device-id }}  ",
                filterChain)).containsExactly("  http-protocol-adaptercommands" + DEVICE_ID + "  ");

        // pre/postfix
        assertThat(filterChain("-----{{header:gateway-id }}{{  header:source }}{{header:device-id }}-----",
                filterChain)).containsExactly("-----http-protocol-adaptercommands" + DEVICE_ID + "-----");

        // pre/postfix and separators
        assertThat(filterChain("-----{{header:gateway-id }}///{{  header:source }}///{{header:device-id }}-----",
                filterChain)).containsExactly("-----http-protocol-adapter///commands///" + DEVICE_ID + "-----");
    }

    @Test
    public void testInvalidPlaceholderVariations() {

        // illegal braces combinations
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> filterChain("{{he{{ader:namespace }}{{  header:source }}{{header:device-id }}",
                        filterChain));

        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> filterChain("{{he}}ader:namespace }}{{  header:source }}{{header:device-id }}",
                        filterChain));

        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> filterChain("{{header:nam{{espace }}{{  header:source }}{{header:device-id }}",
                        filterChain));

        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> filterChain("{{header:nam}}espace }}{{  header:source }}{{header:device-id }}",
                        filterChain));
    }

    @Test
    public void testValidate() {
        // no whitespace
        PlaceholderFilter.validate("{{header:gateway-id}}/{{header:source}}:{{header:device-id}}", placeholders);

        // multi whitespace
        PlaceholderFilter.validate("{{  header:gateway-id  }}/{{  header:source  }}:{{  header:device-id  }}",
                placeholders);

        // mixed whitespace
        PlaceholderFilter.validate("{{header:gateway-id }}/{{  header:source }}:{{header:device-id }}", placeholders);

        // no separators
        PlaceholderFilter.validate("{{header:gateway-id }}{{  header:source }}{{header:device-id }}", placeholders);

        // whitespace separators
        PlaceholderFilter.validate("{{header:gateway-id }}  {{  header:source }}  {{header:device-id }}", placeholders);

        // pre/postfix whitespace
        PlaceholderFilter.validate("  {{header:gateway-id }}{{  header:source }}{{header:device-id }}  ", placeholders);

        // pre/postfix
        PlaceholderFilter.validate("-----{{header:gateway-id }}{{  header:source }}{{header:device-id }}-----",
                placeholders);

        // pre/postfix and separators
        PlaceholderFilter.validate("-----{{header:gateway-id }}///{{  header:source }}///{{header:device-id }}-----",
                placeholders);

        // placeholders with pipeline functions
        PlaceholderFilter.validate("{{ header:gateway-id }}:{{header:device-id | fn:substring-after(':')}}",
                placeholders);
    }

    @Test
    public void testValidateFails() {
        // unsupported placeholder in parameter position
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> PlaceholderFilter.validate("{{  header:xxx | fn:default(ing:namespace) }}",
                        placeholders));

        // illegal braces combinations
        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> PlaceholderFilter.validate("{{he{{ader:gateway-id }}{{  header:source }}{{header:device-id }}",
                        placeholders));

        assertThatExceptionOfType(UnresolvedPlaceholderException.class).isThrownBy(
                () -> PlaceholderFilter.validate("{{he}}ader:gateway-id }}{{  header:source }}{{header:device-id }}",
                        placeholders));

        // All header names are supported.
        assertThatCode(
                () -> PlaceholderFilter.validate("{{header:gat{{eway-id }}{{  header:source }}{{header:device-id }}",
                        placeholders))
                .doesNotThrowAnyException();

        assertThatCode(
                () -> PlaceholderFilter.validate("{{header:gat}}eway-id }}{{  header:source }}{{header:device-id }}",
                        placeholders))
                .doesNotThrowAnyException();

        assertThatExceptionOfType(PlaceholderFunctionTooComplexException.class).isThrownBy(
                () -> PlaceholderFilter.validate(
                        "{{ header:unknown | fn:default('fallback') | fn:upper() | fn:lower() | fn:upper() | fn:lower() | fn:upper() | fn:lower() | fn:upper() | fn:lower() | fn:upper() | fn:lower() }}",
                        placeholders));
    }

    @Test
    public void testValidateAndReplace() {
        final String replacement = UUID.randomUUID().toString();
        // no whitespace
        assertThat(PlaceholderFilter.validateAndReplaceAll("{{header:gateway-id}}/{{header:source}}:{{header:device-id}}",
                replacement, placeholders))
                .containsExactly(String.format("%s/%s:%s", replacement, replacement, replacement));

        // multi whitespace
        assertThat(PlaceholderFilter.validateAndReplaceAll(
                "{{  header:gateway-id  }}/{{  header:source  }}:{{  header:device-id  }}", replacement, placeholders))
                .containsExactly(String.format("%s/%s:%s", replacement, replacement, replacement));

        // mixed whitespace
        assertThat(PlaceholderFilter.validateAndReplaceAll(
                "{{header:gateway-id }}/{{  header:source }}:{{header:device-id }}",
                replacement, placeholders))
                .containsExactly(String.format("%s/%s:%s", replacement, replacement, replacement));

        // no separators
        assertThat(
                PlaceholderFilter.validateAndReplaceAll("{{header:gateway-id }}{{  header:source }}{{header:device-id }}",
                        replacement, placeholders))
                .containsExactly(String.format("%s%s%s", replacement, replacement, replacement));

        // whitespace separators
        assertThat(
                PlaceholderFilter.validateAndReplaceAll(
                        "{{header:gateway-id }}  {{  header:source }}  {{header:device-id }}",
                        replacement, placeholders))
                .containsExactly(String.format("%s  %s  %s", replacement, replacement, replacement));

        // pre/postfix whitespace
        assertThat(
                PlaceholderFilter.validateAndReplaceAll(
                        "  {{header:gateway-id }}{{  header:source }}{{header:device-id }}  ",
                        replacement, placeholders))
                .containsExactly(String.format("  %s%s%s  ", replacement, replacement, replacement));

        // pre/postfix
        assertThat(PlaceholderFilter.validateAndReplaceAll(
                "-----{{header:gateway-id }}{{  header:source }}{{header:device-id }}-----", replacement, placeholders))
                .containsExactly(String.format("-----%s%s%s-----", replacement, replacement, replacement));

        // pre/postfix and separators
        assertThat(PlaceholderFilter.validateAndReplaceAll(
                "-----{{header:gateway-id }}///{{  header:source }}///{{header:device-id }}-----", replacement,
                placeholders))
                .containsExactly(String.format("-----%s///%s///%s-----", replacement, replacement, replacement));
    }

    private static List<String> filterChain(final String template, final PlaceholderResolver... tuples) {
        return PlaceholderFilter.applyForAll(template, PlaceholderFactory.newExpressionResolver(tuples));
    }

}
