/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.headers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.lang.reflect.Field;

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

/**
 * Tests that header values which are JSON keep their JSON representation in their {@link Header}, so that it is not
 * parsed again from the string representation, while the validation of the values is unchanged.
 */
public final class HeaderJsonValueMemoizationTest {

    private static final String AUTH_CONTEXT = DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey();
    private static final String READ_SUBJECTS = DittoHeaderDefinition.READ_SUBJECTS.getKey();
    private static final String REQUESTED_ACKS = DittoHeaderDefinition.REQUESTED_ACKS.getKey();
    private static final String PARTIAL_ACCESS_PATHS = DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey();

    private static final JsonObject AUTH_CONTEXT_JSON = JsonObject.newBuilder()
            .set("type", "pre-authenticated-connection")
            .set("subjects", JsonArray.of(JsonValue.of("integration:subject-1"), JsonValue.of("nginx:ditto")))
            .build();
    private static final JsonArray READ_SUBJECTS_JSON =
            JsonArray.of(JsonValue.of("integration:subject-1"), JsonValue.of("nginx:ditto"));
    private static final JsonArray REQUESTED_ACKS_JSON = JsonArray.of(JsonValue.of("twin-persisted"));

    private static final JsonObject HEADERS_JSON = JsonObject.newBuilder()
            .set("correlation-id", "correlation-1")
            .set(AUTH_CONTEXT, AUTH_CONTEXT_JSON)
            .set(READ_SUBJECTS, READ_SUBJECTS_JSON)
            .set(REQUESTED_ACKS, REQUESTED_ACKS_JSON)
            .set("response-required", true)
            .build();

    @Test
    public void trustedJsonKeepsJsonValues() {
        final DittoHeaders dittoHeaders = DittoHeaders.newFromTrustedJson(HEADERS_JSON);

        assertThat(memoizedValueOf(dittoHeaders, AUTH_CONTEXT)).isSameAs(AUTH_CONTEXT_JSON);
        assertThat(memoizedValueOf(dittoHeaders, READ_SUBJECTS)).isSameAs(READ_SUBJECTS_JSON);
        assertThat(memoizedValueOf(dittoHeaders, REQUESTED_ACKS)).isSameAs(REQUESTED_ACKS_JSON);
        assertThat(dittoHeaders.toJson()).isEqualTo(HEADERS_JSON);
    }

    @Test
    public void validatedJsonKeepsJsonValues() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder(HEADERS_JSON).build();

        assertThat(memoizedValueOf(dittoHeaders, AUTH_CONTEXT)).isSameAs(AUTH_CONTEXT_JSON);
        assertThat(memoizedValueOf(dittoHeaders, READ_SUBJECTS)).isSameAs(READ_SUBJECTS_JSON);
        assertThat(dittoHeaders.toJson()).isEqualTo(HEADERS_JSON);
    }

    @Test
    public void headersFromJsonEqualHeadersFromStrings() {
        final DittoHeaders fromStrings = DittoHeaders.newBuilder()
                .putHeader("correlation-id", "correlation-1")
                .putHeader(AUTH_CONTEXT, AUTH_CONTEXT_JSON.toString())
                .putHeader(READ_SUBJECTS, READ_SUBJECTS_JSON.toString())
                .putHeader(REQUESTED_ACKS, REQUESTED_ACKS_JSON.toString())
                .putHeader("response-required", "true")
                .build();

        assertThat(DittoHeaders.newFromTrustedJson(HEADERS_JSON)).isEqualTo(fromStrings);
        assertThat(DittoHeaders.newBuilder(HEADERS_JSON).build()).isEqualTo(fromStrings);
        assertThat(DittoHeaders.newFromTrustedJson(HEADERS_JSON).toJson()).isEqualTo(fromStrings.toJson());
    }

    @Test
    public void putHeaderKeepsJsonParsedForValidation() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .putHeader(AUTH_CONTEXT, AUTH_CONTEXT_JSON.toString())
                .putHeader(READ_SUBJECTS, READ_SUBJECTS_JSON.toString())
                .putHeader(REQUESTED_ACKS, REQUESTED_ACKS_JSON.toString())
                .build();

        assertThat(memoizedValueOf(dittoHeaders, AUTH_CONTEXT)).isEqualTo(AUTH_CONTEXT_JSON);
        assertThat(memoizedValueOf(dittoHeaders, READ_SUBJECTS)).isEqualTo(READ_SUBJECTS_JSON);
        assertThat(memoizedValueOf(dittoHeaders, REQUESTED_ACKS)).isEqualTo(REQUESTED_ACKS_JSON);
    }

    @Test
    public void putJsonHeaderKeepsJsonValue() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .putJsonHeader(AUTH_CONTEXT, AUTH_CONTEXT_JSON)
                .putJsonHeader(READ_SUBJECTS, READ_SUBJECTS_JSON)
                .build();

        assertThat(memoizedValueOf(dittoHeaders, AUTH_CONTEXT)).isSameAs(AUTH_CONTEXT_JSON);
        assertThat(memoizedValueOf(dittoHeaders, READ_SUBJECTS)).isSameAs(READ_SUBJECTS_JSON);
        assertThat(dittoHeaders).isEqualTo(DittoHeaders.newBuilder()
                .putHeader(AUTH_CONTEXT, AUTH_CONTEXT_JSON.toString())
                .putHeader(READ_SUBJECTS, READ_SUBJECTS_JSON.toString())
                .build());
    }

    @Test
    public void putJsonHeaderWithJsonStringPutsPlainString() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .putJsonHeader("correlation-id", JsonValue.of("correlation-1"))
                .putJsonHeader("x-custom", JsonValue.of("plain"))
                .build();

        assertThat(dittoHeaders.getCorrelationId()).contains("correlation-1");
        assertThat(dittoHeaders.get("x-custom")).isEqualTo("plain");
    }

    @Test
    public void putJsonHeaderValidatesValue() {
        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> DittoHeaders.newBuilder().putJsonHeader(AUTH_CONTEXT, READ_SUBJECTS_JSON));
        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> DittoHeaders.newBuilder()
                        .putJsonHeader(READ_SUBJECTS, JsonArray.of(JsonValue.of(1))));
        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> DittoHeaders.newBuilder()
                        .putJsonHeader(REQUESTED_ACKS, JsonArray.of(JsonValue.of("{invalid label"))));
    }

    @Test
    public void putJsonHeaderWithNullValueThrows() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> DittoHeaders.newBuilder().putJsonHeader(AUTH_CONTEXT, null));
    }

    @Test
    public void invalidJsonHeaderValuesAreStillRejected() {
        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> DittoHeaders.newBuilder().putHeader(AUTH_CONTEXT, "[1]"));
        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> DittoHeaders.newBuilder().putHeader(AUTH_CONTEXT, "{invalid"));
        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> DittoHeaders.newBuilder().putHeader(READ_SUBJECTS, "{}"));
        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> DittoHeaders.newBuilder().putHeader(READ_SUBJECTS, "[1]"));
        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> DittoHeaders.newBuilder().putHeader(REQUESTED_ACKS, "{}"));
        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> DittoHeaders.newBuilder(JsonObject.newBuilder()
                        .set(AUTH_CONTEXT, READ_SUBJECTS_JSON)
                        .build()));
        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> DittoHeaders.newBuilder(JsonObject.newBuilder()
                        .set(READ_SUBJECTS, JsonArray.of(JsonValue.of(1)))
                        .build()));
    }

    @Test
    public void nullLiteralIsStillAcceptedForJsonObjectHeaders() {
        assertThatNoException().isThrownBy(() -> DittoHeaders.newBuilder().putHeader(PARTIAL_ACCESS_PATHS, "null"));
    }

    private static JsonValue memoizedValueOf(final DittoHeaders dittoHeaders, final String key) {
        final Header header = ((AbstractDittoHeaders) dittoHeaders).headers.get(key);
        assertThat(header).as("header <%s>", key).isNotNull();
        try {
            final Field parsedValue = Header.class.getDeclaredField("parsedValue");
            parsedValue.setAccessible(true);
            final Object result = parsedValue.get(header);
            assertThat(result).as("memoized JSON value of header <%s>", key).isInstanceOf(JsonValue.class);
            return (JsonValue) result;
        } catch (final ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

}
