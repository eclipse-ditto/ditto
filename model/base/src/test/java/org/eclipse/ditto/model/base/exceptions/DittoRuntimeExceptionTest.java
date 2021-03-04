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
package org.eclipse.ditto.model.base.exceptions;

import static org.mockito.Mockito.mock;

import java.net.URI;

import org.eclipse.ditto.model.base.assertions.DittoBaseAssertions;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit test for {@link DittoRuntimeException}.
 */
public final class DittoRuntimeExceptionTest {

    private static final String KNOWN_ERROR_CODE = "matrix.glitch";
    private static final HttpStatus KNOWN_STATUS = HttpStatus.SERVICE_UNAVAILABLE;
    private static final String KNOWN_MESSAGE = "A glitch happened in the Matrix!";
    private static final String KNOWN_DESCRIPTION = "This occurs from time to time. Please restart the Matrix service.";
    private static final DittoHeaders KNOWN_DITTO_HEADERS = DittoHeaders.empty();
    private static final Throwable KNOWN_CAUSE = new IllegalStateException("You divided by zero!");
    private static final URI KNOWN_HREF = null;
    private static final DittoRuntimeException KNOWN_DITTO_RUNTIME_EXCEPTION =
            DittoRuntimeException.newBuilder(KNOWN_ERROR_CODE, KNOWN_STATUS)
                    .message(KNOWN_MESSAGE)
                    .description(KNOWN_DESCRIPTION)
                    .dittoHeaders(KNOWN_DITTO_HEADERS)
                    .cause(KNOWN_CAUSE)
                    .href(KNOWN_HREF)
                    .build();


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DittoRuntimeException.class)
                .withIgnoredFields("cause", "stackTrace", "suppressedExceptions")
                .withRedefinedSuperclass()
                .usingGetClass()
                .suppress(Warning.NONFINAL_FIELDS)
                .verify();
    }


    @Test
    public void knownRuntimeExceptionIsLikeExpected() {
        DittoBaseAssertions.assertThat(KNOWN_DITTO_RUNTIME_EXCEPTION)
                .hasErrorCode(KNOWN_ERROR_CODE)
                .hasStatus(KNOWN_STATUS)
                .hasDittoHeaders(KNOWN_DITTO_HEADERS)
                .hasDescription(KNOWN_DESCRIPTION)
                .hasMessage(KNOWN_MESSAGE)
                .hasCause(KNOWN_CAUSE)
                .hasNoHref();
    }


    @Test
    public void copyAndAlterExistingDittoRuntimeException() {
        final DittoHeaders dittoHeadersMock = mock(DittoHeaders.class);

        final DittoRuntimeException withOtherDittoHeaders =
                DittoRuntimeException.newBuilder(KNOWN_DITTO_RUNTIME_EXCEPTION)
                        .dittoHeaders(dittoHeadersMock)
                .build();

        DittoBaseAssertions.assertThat(withOtherDittoHeaders)
                .hasErrorCode(KNOWN_ERROR_CODE)
                .hasStatus(KNOWN_STATUS)
                .hasDittoHeaders(dittoHeadersMock)
                .hasDescription(KNOWN_DESCRIPTION)
                .hasMessage(KNOWN_MESSAGE)
                .hasCause(KNOWN_CAUSE)
                .hasNoHref();
    }

}
