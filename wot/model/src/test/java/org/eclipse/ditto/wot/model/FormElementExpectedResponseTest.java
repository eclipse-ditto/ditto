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
package org.eclipse.ditto.wot.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

/**
 * Unit test for {@link FormElementExpectedResponse} and {@link MutableFormElementExpectedResponseBuilder}.
 */
public final class FormElementExpectedResponseTest {

    private static final String CONTENT_TYPE = "application/json";
    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(FormElementExpectedResponse.JsonFields.CONTENT_TYPE, CONTENT_TYPE)
            .build();

    @Test
    public void fromJsonCreatesExpectedResponse() {
        final FormElementExpectedResponse expectedResponse = FormElementExpectedResponse.fromJson(KNOWN_JSON);

        assertThat(expectedResponse.getContentType()).contains(CONTENT_TYPE);
    }

    @Test
    public void newBuilderCreatesEmptyBuilder() {
        final FormElementExpectedResponse.Builder builder = FormElementExpectedResponse.newBuilder();
        final FormElementExpectedResponse expectedResponse = builder.build();

        assertThat(expectedResponse.getContentType()).isEmpty();
    }

    @Test
    public void newBuilderWithJsonObjectCreatesPopulatedBuilder() {
        final FormElementExpectedResponse.Builder builder = FormElementExpectedResponse.newBuilder(KNOWN_JSON);
        final FormElementExpectedResponse expectedResponse = builder.build();

        assertThat(expectedResponse.getContentType()).contains(CONTENT_TYPE);
    }

    @Test
    public void builderSetContentType() {
        final FormElementExpectedResponse expectedResponse = FormElementExpectedResponse.newBuilder()
                .setContentType(CONTENT_TYPE)
                .build();

        assertThat(expectedResponse.getContentType()).contains(CONTENT_TYPE);
        assertThat(expectedResponse.toJson()).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void builderSetContentTypeWithNull() {
        final FormElementExpectedResponse expectedResponse = FormElementExpectedResponse.newBuilder()
                .setContentType(CONTENT_TYPE)
                .setContentType(null)
                .build();

        assertThat(expectedResponse.getContentType()).isEmpty();
    }

    @Test
    public void toJsonReturnsExpected() {
        final FormElementExpectedResponse expectedResponse = FormElementExpectedResponse.fromJson(KNOWN_JSON);
        final JsonObject actualJson = expectedResponse.toJson();

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void fromJsonToJsonRoundTrip() {
        final FormElementExpectedResponse original = FormElementExpectedResponse.newBuilder()
                .setContentType(CONTENT_TYPE)
                .build();
        final JsonObject json = original.toJson();
        final FormElementExpectedResponse deserialized = FormElementExpectedResponse.fromJson(json);

        assertThat(deserialized.toJson()).isEqualTo(original.toJson());
    }

    @Test
    public void builderChaining() {
        final FormElementExpectedResponse.Builder builder = FormElementExpectedResponse.newBuilder()
                .setContentType(CONTENT_TYPE);

        // Verify builder returns itself for chaining
        assertThat(builder).isInstanceOf(FormElementExpectedResponse.Builder.class);
    }

    @Test
    public void emptyJsonObjectResultsInEmptyContentType() {
        final FormElementExpectedResponse expectedResponse =
                FormElementExpectedResponse.fromJson(JsonObject.empty());

        assertThat(expectedResponse.getContentType()).isEmpty();
    }

    @Test
    public void differentContentTypesAreHandled() {
        final String[] contentTypes = {
                "application/json",
                "application/td+json",
                "text/plain",
                "application/xml",
                "application/octet-stream"
        };

        for (final String ct : contentTypes) {
            final FormElementExpectedResponse response = FormElementExpectedResponse.newBuilder()
                    .setContentType(ct)
                    .build();

            assertThat(response.getContentType())
                    .as("Content type should be: %s", ct)
                    .contains(ct);
        }
    }

}
