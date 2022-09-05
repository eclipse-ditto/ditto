/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.acks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.acks.AcknowledgementRequestParseException}.
 */
public final class AcknowledgementRequestParseExceptionTest {

    private static final JsonObject KNOWN_JSON_REPRESENTATION = JsonObject.newBuilder()
            .set(DittoRuntimeException.JsonFields.ERROR_CODE, AcknowledgementRequestParseException.ERROR_CODE)
            .set(DittoRuntimeException.JsonFields.MESSAGE,
                    MessageFormat.format(AcknowledgementRequestParseException.MESSAGE_TEMPLATE, "ab"))
            .set(DittoRuntimeException.JsonFields.DESCRIPTION, AcknowledgementRequestParseException.DEFAULT_DESCRIPTION)
            .set(DittoRuntimeException.JsonFields.STATUS, HttpStatus.UNPROCESSABLE_ENTITY.getCode())
            .build();

    @Rule
    public final TestName testName = new TestName();

    @Test
    public void assertImmutability() {
        assertInstancesOf(AcknowledgementRequestParseException.class, areImmutable());
    }

    @Test
    public void causeMessageIsRegardedInMessage() {
        final String invalidStringRepresentation = "ab";
        final AcknowledgementLabelInvalidException cause =
                new AcknowledgementLabelInvalidException(invalidStringRepresentation);
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();

        final AcknowledgementRequestParseException underTest =
                new AcknowledgementRequestParseException(invalidStringRepresentation, cause, dittoHeaders);

        assertThat(underTest.getMessage())
                .contains(MessageFormat.format(AcknowledgementRequestParseException.MESSAGE_TEMPLATE,
                        invalidStringRepresentation))
                .contains(cause.getMessage());
    }

    @Test
    public void toJsonReturnsExpected() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
        final AcknowledgementRequestParseException underTest =
                new AcknowledgementRequestParseException("ab", null, dittoHeaders);

        final JsonObject actual = underTest.toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON_REPRESENTATION);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();
        final AcknowledgementRequestParseException expected =
                new AcknowledgementRequestParseException("ab", null, dittoHeaders);

        final AcknowledgementRequestParseException actual =
                AcknowledgementRequestParseException.fromJson(KNOWN_JSON_REPRESENTATION, dittoHeaders);

        assertThat(actual).isEqualTo(expected);
    }

}
