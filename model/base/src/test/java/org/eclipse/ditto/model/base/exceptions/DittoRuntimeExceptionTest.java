/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.base.exceptions;

import static org.mockito.Mockito.mock;

import java.net.URI;

import org.eclipse.ditto.model.base.assertions.DittoBaseAssertions;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DittoRuntimeException}.
 */
public final class DittoRuntimeExceptionTest {

    private static final String KNOWN_ERROR_CODE = "matrix.glitch";
    private static final HttpStatusCode KNOWN_STATUS_CODE = HttpStatusCode.SERVICE_UNAVAILABLE;
    private static final String KNOWN_MESSAGE = "A glitch happened in the Matrix!";
    private static final String KNOWN_DESCRIPTION = "This occurs from time to time. Please restart the Matrix service.";
    private static final DittoHeaders KNOWN_DITTO_HEADERS = DittoHeaders.empty();
    private static final Throwable KNOWN_CAUSE = new IllegalStateException("You divided by zero!");
    private static final URI KNOWN_HREF = null;
    private static final DittoRuntimeException KNOWN_DITTO_RUNTIME_EXCEPTION =
            DittoRuntimeException.newBuilder(KNOWN_ERROR_CODE, KNOWN_STATUS_CODE) //
                    .message(KNOWN_MESSAGE) //
                    .description(KNOWN_DESCRIPTION) //
                    .dittoHeaders(KNOWN_DITTO_HEADERS) //
                    .cause(KNOWN_CAUSE) //
                    .href(KNOWN_HREF) //
                    .build();


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DittoRuntimeException.class)
                .withIgnoredFields("detailMessage", "cause", "stackTrace", "suppressedExceptions")
                .withRedefinedSuperclass()
                .usingGetClass()
                .verify();
    }


    @Test
    public void knownRuntimeExceptionIsLikeExpected() {
        DittoBaseAssertions.assertThat(KNOWN_DITTO_RUNTIME_EXCEPTION) //
                .hasErrorCode(KNOWN_ERROR_CODE) //
                .hasStatusCode(KNOWN_STATUS_CODE) //
                .hasDittoHeaders(KNOWN_DITTO_HEADERS) //
                .hasDescription(KNOWN_DESCRIPTION) //
                .hasMessage(KNOWN_MESSAGE) //
                .hasCause(KNOWN_CAUSE) //
                .hasNoHref();
    }


    @Test
    public void copyAndAlterExistingDittoRuntimeException() {
        final DittoHeaders dittoHeadersMock = mock(DittoHeaders.class);

        final DittoRuntimeException withOtherDittoHeaders =
                DittoRuntimeException.newBuilder(KNOWN_DITTO_RUNTIME_EXCEPTION) //
                        .dittoHeaders(dittoHeadersMock) //
                .build();

        DittoBaseAssertions.assertThat(withOtherDittoHeaders) //
                .hasErrorCode(KNOWN_ERROR_CODE) //
                .hasStatusCode(KNOWN_STATUS_CODE) //
                .hasDittoHeaders(dittoHeadersMock) //
                .hasDescription(KNOWN_DESCRIPTION) //
                .hasMessage(KNOWN_MESSAGE) //
                .hasCause(KNOWN_CAUSE) //
                .hasNoHref();
    }

}
