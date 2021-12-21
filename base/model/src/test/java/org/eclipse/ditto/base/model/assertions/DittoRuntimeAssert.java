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
package org.eclipse.ditto.base.model.assertions;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Optional;

import org.assertj.core.api.AbstractAssert;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;

/**
 * Specific assertion for {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException} objects.
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
        assertThat(actualErrorCode)
                .overridingErrorMessage("Expected error code of DittoRuntimeException to be \n<%s> but it was \n<%s>",
                        expectedErrorCode, actualErrorCode)
                .isEqualTo(expectedErrorCode);

        return this;
    }

    /**
     * @since 2.0.0
     */
    public DittoRuntimeAssert hasStatus(final HttpStatus expectedStatus) {
        isNotNull();

        final HttpStatus actualStatus = actual.getHttpStatus();
        assertThat(actualStatus)
                .overridingErrorMessage("Expected status of DittoRuntimeException to be \n<%s> but it was \n<%s>",
                        expectedStatus, actualStatus)
                .isEqualTo(expectedStatus);

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
        final HttpStatus actualStatus = actual.getHttpStatus();
        final int actualStatusCode = actualStatus.getCode();
        assertThat(actualStatusCode)
                .overridingErrorMessage("Expected status code of DittoRuntimeException to be\n<%s> but it was\n<%s>",
                        expectedValue, actualStatusCode)
                .isEqualTo(expectedValue);

        return myself;
    }

    public DittoRuntimeAssert hasDittoHeaders(final DittoHeaders expectedDittoHeaders) {
        isNotNull();

        final DittoHeaders actualDittoHeaders = actual.getDittoHeaders();
        assertThat(actualDittoHeaders)
                .overridingErrorMessage(
                        "Expected command headers of DittoRuntimeException to be \n<%s> but they were " + "\n<%s>",
                        expectedDittoHeaders, actualDittoHeaders)
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
        assertThat(actualMessage)
                .overridingErrorMessage("Expected message of DittoRuntimeException to be \n<%s> but it was \n<%s>",
                        expectedMessage, actualMessage)
                .isEqualTo(expectedMessage);

        return this;
    }

    public DittoRuntimeAssert hasCause(final Throwable expectedCause) {
        isNotNull();

        final Throwable actualCause = actual.getCause();
        assertThat(actualCause)
                .overridingErrorMessage("Expected cause of DittoRuntimeException to be \n<%s> but it was \n<%s>",
                        expectedCause, actualCause)
                .isEqualTo(expectedCause);

        return this;
    }

    public DittoRuntimeAssert hasNoCause() {
        isNotNull();

        final Throwable actualCause = actual.getCause();
        assertThat(actualCause).as("Expected no cause").isNull();

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
