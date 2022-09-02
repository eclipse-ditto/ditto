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
package org.eclipse.ditto.base.model.signals;

import static org.eclipse.ditto.base.model.assertions.DittoBaseAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URI;
import java.text.MessageFormat;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Unit test for {@link UnsupportedSignalException}.
 */
public final class UnsupportedSignalExceptionTest {

    private static final String UNKNOWN_SIGNAL = "zoiglfrex";

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    private DittoHeaders dittoHeaders;

    @Before
    public void before() {
        dittoHeaders = DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(UnsupportedSignalException.class, areImmutable());
    }

    @Test
    public void buildInstanceFromSignalAndDittoHeaders() {
        final UnsupportedSignalException unsupportedSignalException =
                UnsupportedSignalException.newBuilder(UNKNOWN_SIGNAL).dittoHeaders(dittoHeaders).build();

        assertThat(unsupportedSignalException)
                .isNotNull()
                .hasMessage(MessageFormat.format(UnsupportedSignalException.MESSAGE_TEMPLATE, UNKNOWN_SIGNAL))
                .hasDescription(UnsupportedSignalException.DEFAULT_DESCRIPTION)
                .hasDittoHeaders(dittoHeaders)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasNoCause()
                .hasNoHref();
    }

    @Test
    public void instanceFromSignalAndDittoHeadersToJsonReturnsExpected() {
        final UnsupportedSignalException unsupportedSignalException =
                UnsupportedSignalException.newBuilder(UNKNOWN_SIGNAL).dittoHeaders(dittoHeaders).build();

        final JsonObject jsonObject = JsonObject.newBuilder()
                .set(DittoRuntimeException.JsonFields.ERROR_CODE, unsupportedSignalException.getErrorCode())
                .set(DittoRuntimeException.JsonFields.MESSAGE, unsupportedSignalException.getMessage())
                .set(DittoRuntimeException.JsonFields.DESCRIPTION,
                        unsupportedSignalException.getDescription().orElse(null))
                .set(DittoRuntimeException.JsonFields.STATUS, unsupportedSignalException.getHttpStatus().getCode())
                .build();

        assertThat(unsupportedSignalException.toJson()).isEqualTo(jsonObject);
    }

    @Test
    public void fromJsonReturnsExpectedInstanceFromSignalAndDittoHeaders() {
        final UnsupportedSignalException unsupportedSignalException =
                UnsupportedSignalException.newBuilder(UNKNOWN_SIGNAL).dittoHeaders(dittoHeaders).build();
        final JsonObject jsonObject = unsupportedSignalException.toJson();

        assertThat(UnsupportedSignalException.fromJson(jsonObject, unsupportedSignalException.getDittoHeaders()))
                .isEqualTo(unsupportedSignalException);
    }

    @Test
    public void setDittoHeadersWorksAsExpected() {
        final HttpStatus httpStatus = HttpStatus.UNPROCESSABLE_ENTITY;
        final String message = "This is the message.";
        final String description = "This is the description";
        final IllegalStateException illegalStateException = new IllegalStateException("Just for this test.");
        final URI href = URI.create("https://www.example.com/");
        final DittoHeaders otherHeaders = DittoHeaders.newBuilder(dittoHeaders)
                .responseRequired(true)
                .putHeader("foo", "bar")
                .build();
        final UnsupportedSignalException underTest = UnsupportedSignalException.newBuilder(UNKNOWN_SIGNAL)
                .httpStatus(httpStatus)
                .message(message)
                .description(description)
                .dittoHeaders(dittoHeaders)
                .cause(illegalStateException)
                .href(href)
                .build();

        final DittoRuntimeException underTestWithOtherHeaders = underTest.setDittoHeaders(otherHeaders);

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(underTestWithOtherHeaders.getDittoHeaders())
                    .as("DittoHeaders")
                    .isEqualTo(otherHeaders);
            softly.assertThat(underTestWithOtherHeaders.getMessage()).as("message").isEqualTo(underTest.getMessage());
            softly.assertThat(underTestWithOtherHeaders.getDescription())
                    .as("description")
                    .isEqualTo(underTest.getDescription());
            softly.assertThat(underTestWithOtherHeaders.getHttpStatus())
                    .as("HTTP status")
                    .isEqualTo(underTest.getHttpStatus());
            softly.assertThat(underTestWithOtherHeaders.getCause()).as("cause").isEqualTo(underTest.getCause());
            softly.assertThat(underTestWithOtherHeaders.getHref()).as("href").isEqualTo(underTest.getHref());
        }
    }

    @Test
    public void setSuccessHttpStatusToBuilderThrowsException() {
        final HttpStatus httpStatus = HttpStatus.OK;

        Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> UnsupportedSignalException.newBuilder(UNKNOWN_SIGNAL).httpStatus(httpStatus).build())
                .withMessage("Category of <%s> is neither client error nor server error.", httpStatus)
                .withNoCause();
    }

    @Test
    public void instanceFromMessageReturnsExpected() {
        final String message = "This is the detail message.";

        final UnsupportedSignalException unsupportedSignalException =
                UnsupportedSignalException.fromMessage(message, dittoHeaders);

        assertThat(unsupportedSignalException)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasMessage(message)
                .hasDescription(UnsupportedSignalException.DEFAULT_DESCRIPTION)
                .hasNoHref()
                .hasNoCause();
    }

}