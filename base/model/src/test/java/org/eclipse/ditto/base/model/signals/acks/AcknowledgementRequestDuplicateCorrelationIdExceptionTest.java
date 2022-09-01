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
package org.eclipse.ditto.base.model.signals.acks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;
import java.util.UUID;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

/**
 * Unit test for {@link AcknowledgementRequestDuplicateCorrelationIdException}.
 */
public final class AcknowledgementRequestDuplicateCorrelationIdExceptionTest {

    private static final String KNOWN_CORRELATION_ID = UUID.randomUUID().toString();
    private static final JsonObject KNOWN_JSON_REPRESENTATION = JsonObject.newBuilder()
            .set(DittoRuntimeException.JsonFields.ERROR_CODE,
                    AcknowledgementRequestDuplicateCorrelationIdException.ERROR_CODE)
            .set(DittoRuntimeException.JsonFields.MESSAGE,
                    MessageFormat.format(AcknowledgementRequestDuplicateCorrelationIdException.MESSAGE_TEMPLATE,
                            KNOWN_CORRELATION_ID))
            .set(DittoRuntimeException.JsonFields.DESCRIPTION,
                    AcknowledgementRequestDuplicateCorrelationIdException.DEFAULT_DESCRIPTION)
            .set(DittoRuntimeException.JsonFields.STATUS, HttpStatus.CONFLICT.getCode())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(AcknowledgementRequestDuplicateCorrelationIdException.class, areImmutable());
    }

    @Test
    public void toJsonReturnsExpected() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(KNOWN_CORRELATION_ID).build();
        final AcknowledgementRequestDuplicateCorrelationIdException underTest =
                AcknowledgementRequestDuplicateCorrelationIdException.newBuilder(KNOWN_CORRELATION_ID)
                        .dittoHeaders(dittoHeaders).build();

        final JsonObject actual = underTest.toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON_REPRESENTATION);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(KNOWN_CORRELATION_ID).build();
        final AcknowledgementRequestDuplicateCorrelationIdException expected =
                AcknowledgementRequestDuplicateCorrelationIdException.newBuilder(KNOWN_CORRELATION_ID)
                        .dittoHeaders(dittoHeaders)
                        .build();

        final AcknowledgementRequestDuplicateCorrelationIdException actual =
                AcknowledgementRequestDuplicateCorrelationIdException.fromJson(KNOWN_JSON_REPRESENTATION, dittoHeaders);

        assertThat(actual).isEqualTo(expected);
    }
}
