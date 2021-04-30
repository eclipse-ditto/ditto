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
package org.eclipse.ditto.base.model.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Rule;
import org.junit.Test;

/**
 * Unit test for {@link DittoHeaderInvalidException}.
 */
public final class DittoHeaderInvalidExceptionTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void assertImmutability() {
        assertInstancesOf(DittoHeaderInvalidException.class, areImmutable());
    }

    @Test
    public void buildFromInvalidHeaderKey() {
        final String invalidHeaderKey = "plumbus";
        final DittoHeaderInvalidException underTest = DittoHeaderInvalidException.newBuilder()
                .withInvalidHeaderKey(invalidHeaderKey)
                .build();

        assertThat(underTest.getInvalidHeaderKey()).hasValue(invalidHeaderKey);
    }

    @Test
    public void buildFromCustomMessage() {
        final String customMessage = "theCustomMessage";

        final DittoHeaderInvalidException headerInvalidException = DittoHeaderInvalidException.newBuilder()
                .message(customMessage)
                .build();

        softly.assertThat(headerInvalidException.getInvalidHeaderKey()).as("invalid header key").isEmpty();
        softly.assertThat(headerInvalidException.getMessage()).as("message").isEqualTo(customMessage);
        softly.assertThat(headerInvalidException.getDescription())
                .as("description")
                .hasValue("Verify that the header has the correct syntax and try again.");
    }

    @Test
    public void buildForInvalidType() {
        final String invalidHeaderKey = "theHeaderName";
        final DittoHeaderInvalidException headerInvalidException =
                DittoHeaderInvalidException.newInvalidTypeBuilder(invalidHeaderKey, "theValue", "theExpectedType")
                        .build();

        softly.assertThat(headerInvalidException.getInvalidHeaderKey())
                .as("invalid header key")
                .hasValue(invalidHeaderKey);
        softly.assertThat(headerInvalidException.getMessage())
                .as("message")
                .isEqualTo("The value 'theValue' of the header 'theHeaderName' is not a valid theExpectedType.");
        softly.assertThat(headerInvalidException.getDescription())
                .as("description")
                .hasValue("Verify that the value of the header 'theHeaderName' is a valid 'theExpectedType' " +
                        "and try again.");
    }

    @Test
    public void toJsonReturnsExpected() {
        final String invalidHeaderKey = "void";
        final String message = "The header key <" + invalidHeaderKey + "> is invalid.";
        final String description = "YOLO!";
        final String href = "https://www.example.com";

        final JsonObject jsonObject = JsonObject.newBuilder()
                .set(DittoRuntimeException.JsonFields.STATUS, HttpStatus.BAD_REQUEST.getCode())
                .set(DittoRuntimeException.JsonFields.ERROR_CODE, DittoHeaderInvalidException.ERROR_CODE)
                .set(DittoRuntimeException.JsonFields.MESSAGE, message)
                .set(DittoRuntimeException.JsonFields.DESCRIPTION, description)
                .set(DittoRuntimeException.JsonFields.HREF, href)
                .set(DittoHeaderInvalidException.JSON_FIELD_INVALID_HEADER_KEY, invalidHeaderKey)
                .build();

        final DittoHeaderInvalidException underTest = DittoHeaderInvalidException.newBuilder()
                .withInvalidHeaderKey(invalidHeaderKey)
                .message(message)
                .description(description)
                .href(href)
                .build();

        assertThat(underTest.toJson()).isEqualTo(jsonObject);
    }

    @Test
    public void fromValidJsonReturnsExpected() {
        final String invalidHeaderKey = "void";
        final String message = "The header key <" + invalidHeaderKey + "> is invalid.";
        final String description = "YOLO!";
        final String href = "https://www.example.com";

        final DittoHeaderInvalidException underTest = DittoHeaderInvalidException.newBuilder()
                .withInvalidHeaderKey(invalidHeaderKey)
                .message(message)
                .description(description)
                .href(href)
                .build();

        assertThat(DittoHeaderInvalidException.fromJson(underTest.toJson(), DittoHeaders.empty())).isEqualTo(underTest);
    }

}
