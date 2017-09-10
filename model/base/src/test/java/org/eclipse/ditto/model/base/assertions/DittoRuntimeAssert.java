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
package org.eclipse.ditto.model.base.assertions;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Optional;

import org.assertj.core.api.AbstractAssert;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;


/**
 * Specific assertion for {@link DittoRuntimeException} objects.
 */
public final class DittoRuntimeAssert extends AbstractAssert<DittoRuntimeAssert, DittoRuntimeException> {

    /**
     * Constructs a new {@code DittoRuntimeAssert} object.
     *
     * @param actual the actual DittoRuntimeException.
     */
    DittoRuntimeAssert(final DittoRuntimeException actual) {
        super(actual, DittoRuntimeAssert.class);
    }

    public DittoRuntimeAssert hasErrorCode(final String expectedErrorCode) {
        isNotNull();

        final String actualErrorCode = actual.getErrorCode();
        assertThat(actualErrorCode) //
                .overridingErrorMessage("Expected error code of DittoRuntimeException to be \n<%s> but it was \n<%s>",
                        expectedErrorCode, actualErrorCode) //
                .isEqualTo(expectedErrorCode);

        return this;
    }

    public DittoRuntimeAssert hasStatusCode(final HttpStatusCode expectedStatusCode) {
        isNotNull();

        final HttpStatusCode actualStatusCode = actual.getStatusCode();
        assertThat(actualStatusCode) //
                .overridingErrorMessage("Expected status code of DittoRuntimeException to be \n<%s> but it was \n<%s>",
                        expectedStatusCode, actualStatusCode) //
                .isEqualTo(expectedStatusCode);

        return this;
    }

    /**
     * Checks if this DittoRuntimeException has the expected HTTP status code.
     *
     * @param expectedValue the expected HTTP status code.
     * @return this Assert instance to allow Method chaining.
     */
    public DittoRuntimeAssert hasStatusCodeValue(final int expectedValue) {
        isNotNull();
        final HttpStatusCode actualStatusCode = actual.getStatusCode();
        final int actualStatusCodeValue = actualStatusCode.toInt();
        assertThat(actualStatusCodeValue)
                .overridingErrorMessage("Expected status code value of DittoRuntimeException to be\n<%s> but it " +
                        "was\n<%s>", expectedValue, actualStatusCodeValue)
                .isEqualTo(expectedValue);
        return myself;
    }

    public DittoRuntimeAssert hasDittoHeaders(final DittoHeaders expectedDittoHeaders) {
        isNotNull();

        final DittoHeaders actualDittoHeaders = actual.getDittoHeaders();
        assertThat(actualDittoHeaders) //
                .overridingErrorMessage(
                        "Expected command headers of DittoRuntimeException to be \n<%s> but they were " + "\n<%s>",
                        expectedDittoHeaders, actualDittoHeaders) //
                .isEqualTo(expectedDittoHeaders);

        return this;
    }

    public DittoRuntimeAssert hasDescription(final String expectedDescription) {
        isNotNull();

        final Optional<String> actualDescriptionOptional = actual.getDescription();
        assertThat(actualDescriptionOptional).contains(expectedDescription);

        return this;
    }

    public DittoRuntimeAssert hasMessage(final String expectedMessage) {
        isNotNull();

        final String actualMessage = actual.getMessage();
        assertThat(actualMessage) //
                .overridingErrorMessage("Expected message of DittoRuntimeException to be \n<%s> but it was \n<%s>",
                        expectedMessage, actualMessage) //
                .isEqualTo(expectedMessage);

        return this;
    }

    public DittoRuntimeAssert hasCause(final Throwable expectedCause) {
        isNotNull();

        final Throwable actualCause = actual.getCause();
        assertThat(actualCause) //
                .overridingErrorMessage("Expected cause of DittoRuntimeException to be \n<%s> but it was \n<%s>",
                        expectedCause,
                        actualCause) //
                .isEqualTo(expectedCause);

        return this;
    }

    public DittoRuntimeAssert hasHref(final URI expectedHref) {
        isNotNull();

        final Optional<URI> actualHrefOptional = actual.getHref();
        assertThat(actualHrefOptional).contains(expectedHref);

        return this;
    }

    public DittoRuntimeAssert hasNoHref() {
        isNotNull();

        final Optional<URI> actualHrefOptional = actual.getHref();
        assertThat(actualHrefOptional).isEmpty();

        return this;
    }

}
