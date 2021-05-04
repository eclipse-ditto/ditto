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
import java.time.Duration;
import java.util.UUID;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.junit.Test;

/**
 * Unit test for {@link AcknowledgementRequestTimeoutException}.
 */
public final class AcknowledgementRequestTimeoutExceptionTest {

    private static final String KNOWN_CORRELATION_ID = UUID.randomUUID().toString();
    private static final Duration KNOWN_TIMEOUT = Duration.ofMillis(1337);

    private static final JsonObject KNOWN_JSON_REPRESENTATION = JsonObject.newBuilder()
            .set(DittoRuntimeException.JsonFields.ERROR_CODE, AcknowledgementRequestTimeoutException.ERROR_CODE)
            .set(DittoRuntimeException.JsonFields.MESSAGE,
                    MessageFormat.format(AcknowledgementRequestTimeoutException.MESSAGE_TEMPLATE,
                            KNOWN_TIMEOUT.toMillis()))
            .set(DittoRuntimeException.JsonFields.DESCRIPTION,
                    AcknowledgementRequestTimeoutException.DEFAULT_DESCRIPTION)
            .set(DittoRuntimeException.JsonFields.STATUS, HttpStatus.REQUEST_TIMEOUT.getCode())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(AcknowledgementRequestTimeoutException.class, areImmutable());
    }

    @Test
    public void toJsonReturnsExpected() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(KNOWN_CORRELATION_ID).build();
        final AcknowledgementRequestTimeoutException underTest =
                AcknowledgementRequestTimeoutException.newBuilder(KNOWN_TIMEOUT)
                        .dittoHeaders(dittoHeaders)
                        .build();

        final JsonObject actual = underTest.toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON_REPRESENTATION);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().correlationId(KNOWN_CORRELATION_ID).build();
        final AcknowledgementRequestTimeoutException expected =
                AcknowledgementRequestTimeoutException.newBuilder(KNOWN_TIMEOUT)
                        .dittoHeaders(dittoHeaders)
                        .build();

        final AcknowledgementRequestTimeoutException actual =
                AcknowledgementRequestTimeoutException.fromJson(KNOWN_JSON_REPRESENTATION, dittoHeaders);

        assertThat(actual).isEqualTo(expected);
    }

}
