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
package org.eclipse.ditto.signals.commands.base.assertions;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.base.assertions.WithDittoHeadersChecker;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;

/**
 * An assert for {@link ErrorResponse}.
 */
public class ErrorResponseAssert extends AbstractAssert<ErrorResponseAssert, ErrorResponse> {

    private final WithDittoHeadersChecker withDittoHeadersChecker;

    public ErrorResponseAssert(final ErrorResponse actual) {
        super(actual, ErrorResponseAssert.class);
        withDittoHeadersChecker = new WithDittoHeadersChecker(actual);
    }

    public ErrorResponseAssert hasDittoHeaders(final DittoHeaders expectedDittoHeaders) {
        isNotNull();
        withDittoHeadersChecker.hasDittoHeaders(expectedDittoHeaders);
        return myself;
    }

    public ErrorResponseAssert hasEmptyDittoHeaders() {
        isNotNull();
        withDittoHeadersChecker.hasEmptyDittoHeaders();
        return myself;
    }

    public ErrorResponseAssert hasCorrelationId(final CharSequence expectedCorrelationId) {
        isNotNull();
        withDittoHeadersChecker.hasCorrelationId(expectedCorrelationId);
        return myself;
    }

    public ErrorResponseAssert hasType(final String expectedType) {
        return assertThatEquals(actual.getType(), expectedType, "type");
    }

    public ErrorResponseAssert hasStatusCode(final HttpStatusCode expectedStatusCode) {
        return assertThatEquals(actual.getStatusCode(), expectedStatusCode, "HTTP status code");
    }

    public ErrorResponseAssert contains(final DittoRuntimeException expectedDittoRuntimeException) {
        return assertThatEquals(actual.getDittoRuntimeException(), expectedDittoRuntimeException,
                "DittoRuntimeException");
    }

    public ErrorResponseAssert
    containsDittoRuntimeExceptionOfType(final Class<? extends DittoRuntimeException> expectedType) {
        isNotNull();
        final DittoRuntimeException actualDittoRuntimeException = actual.getDittoRuntimeException();
        Assertions.assertThat(actualDittoRuntimeException)
                .overridingErrorMessage(
                        "Expected Thing Error Response to " + "contain DittoRuntimeException of " +
                                "type <%s> but it did not",
                        expectedType)
                .isInstanceOf(expectedType);
        return this;
    }

    public ErrorResponseAssert dittoRuntimeExceptionHasErrorCode(final String expectedErrorCode) {
        isNotNull();
        final DittoRuntimeException dittoRuntimeException = actual.getDittoRuntimeException();
        final String actualErrorCode = dittoRuntimeException.getErrorCode();
        Assertions.assertThat(actualErrorCode)
                .overridingErrorMessage(
                        "Expected DittoRuntimeException of Thing Error Response to have error code \n<%s> but it " +
                                "had \n<%s>",
                        expectedErrorCode, actualErrorCode)
                .isEqualTo(expectedErrorCode);
        return this;
    }

    private <T> ErrorResponseAssert assertThatEquals(final T actual, final T expected, final String propertyName) {
        isNotNull();
        Assertions.assertThat(actual)
                .overridingErrorMessage("Expected Thing Error Response to have %s " + "\n<%s> but it had \n<%s>",
                        propertyName,
                        expected, actual)
                .isEqualTo(expected);
        return this;
    }

}
