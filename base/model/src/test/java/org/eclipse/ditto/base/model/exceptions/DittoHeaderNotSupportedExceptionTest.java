/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
 * Unit test for {@link DittoHeaderNotSupportedException}.
 */
public class DittoHeaderNotSupportedExceptionTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void assertImmutability() {
        assertInstancesOf(DittoHeaderNotSupportedException.class, areImmutable());
    }

    @Test
    public void buildFromInvalidHeaderKey() {
        final String invalidHeaderKey = "plumbus";
        final DittoHeaderNotSupportedException underTest = DittoHeaderNotSupportedException.newBuilder()
                .withNotSupportedHeaderKey(invalidHeaderKey)
                .build();

        assertThat(underTest.getNotSupportedHeaderKey()).hasValue(invalidHeaderKey);
    }

    @Test
    public void buildFromCustomMessage() {
        final String customMessage = "theCustomMessage";

        final DittoHeaderNotSupportedException headerInvalidException = DittoHeaderNotSupportedException.newBuilder()
                .message(customMessage)
                .build();

        softly.assertThat(headerInvalidException.getNotSupportedHeaderKey()).as("invalid header key").isEmpty();
        softly.assertThat(headerInvalidException.getMessage()).as("message").isEqualTo(customMessage);
        softly.assertThat(headerInvalidException.getDescription())
                .as("description")
                .hasValue("Verify that the header has the correct syntax and is used on the correct resource level.");
    }

    @Test
    public void buildForInvalidType() {
        final String invalidHeaderKey = "theHeaderName";
        final DittoHeaderNotSupportedException headerInvalidException =
                DittoHeaderNotSupportedException.newInvalidTypeBuilder(invalidHeaderKey, "theValue")
                        .build();

        softly.assertThat(headerInvalidException.getNotSupportedHeaderKey())
                .as("header not supported")
                .hasValue(invalidHeaderKey);
        softly.assertThat(headerInvalidException.getMessage())
                .as("message")
                .isEqualTo("The value 'theValue' of the header 'theHeaderName' is not supported on this resource level.");
    }

    @Test
    public void toJsonReturnsExpected() {
        final String invalidHeaderKey = "void";
        final String message = "The header key <" + invalidHeaderKey + "> is not supported.";
        final String description = "YOLO!";
        final String href = "https://www.example.com";

        final JsonObject jsonObject = JsonObject.newBuilder()
                .set(DittoRuntimeException.JsonFields.STATUS, HttpStatus.BAD_REQUEST.getCode())
                .set(DittoRuntimeException.JsonFields.ERROR_CODE, DittoHeaderNotSupportedException.ERROR_CODE)
                .set(DittoRuntimeException.JsonFields.MESSAGE, message)
                .set(DittoRuntimeException.JsonFields.DESCRIPTION, description)
                .set(DittoRuntimeException.JsonFields.HREF, href)
                .set(DittoHeaderNotSupportedException.JSON_FIELD_NOT_SUPPORTED_HEADER_KEY, invalidHeaderKey)
                .build();

        final DittoHeaderNotSupportedException underTest = DittoHeaderNotSupportedException.newBuilder()
                .withNotSupportedHeaderKey(invalidHeaderKey)
                .message(message)
                .description(description)
                .href(href)
                .build();

        assertThat(underTest.toJson()).isEqualTo(jsonObject);
    }

    @Test
    public void fromValidJsonReturnsExpected() {
        final String invalidHeaderKey = "void";
        final String message = "The header key <" + invalidHeaderKey + "> is not supported on this resource level theExpectedType.";
        final String description = "YOLO!";
        final String href = "https://www.example.com";

        final DittoHeaderNotSupportedException underTest = DittoHeaderNotSupportedException.newBuilder()
                .withNotSupportedHeaderKey(invalidHeaderKey)
                .message(message)
                .description(description)
                .href(href)
                .build();

        assertThat(DittoHeaderNotSupportedException.fromJson(underTest.toJson(), DittoHeaders.empty())).isEqualTo(
                underTest);
    }

}