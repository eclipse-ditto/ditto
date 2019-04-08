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
package org.eclipse.ditto.signals.commands.things.assertions;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.base.assertions.WithDittoHeadersChecker;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;

/**
 * An assert for {@link ThingErrorResponse}.
 */
public class ThingErrorResponseAssert extends AbstractAssert<ThingErrorResponseAssert, ThingErrorResponse> {

    private final WithDittoHeadersChecker withDittoHeadersChecker;

    public ThingErrorResponseAssert(final ThingErrorResponse actual) {
        super(actual, ThingErrorResponseAssert.class);
        withDittoHeadersChecker = new WithDittoHeadersChecker(actual);
    }

    public ThingErrorResponseAssert hasDittoHeaders(final DittoHeaders expectedDittoHeaders) {
        isNotNull();
        withDittoHeadersChecker.hasDittoHeaders(expectedDittoHeaders);
        return myself;
    }

    public ThingErrorResponseAssert hasEmptyDittoHeaders() {
        isNotNull();
        withDittoHeadersChecker.hasEmptyDittoHeaders();
        return myself;
    }

    public ThingErrorResponseAssert hasCorrelationId(final CharSequence expectedCorrelationId) {
        isNotNull();
        withDittoHeadersChecker.hasCorrelationId(expectedCorrelationId);
        return myself;
    }

    public ThingErrorResponseAssert hasType(final String expectedType) {
        return assertThatEquals(actual.getType(), expectedType, "type");
    }

    public ThingErrorResponseAssert hasStatusCode(final HttpStatusCode expectedStatusCode) {
        return assertThatEquals(actual.getStatusCode(), expectedStatusCode, "HTTP status code");
    }

    public ThingErrorResponseAssert contains(final DittoRuntimeException expectedDittoRuntimeException) {
        return assertThatEquals(actual.getDittoRuntimeException(), expectedDittoRuntimeException,
                "DittoRuntimeException");
    }

    public ThingErrorResponseAssert
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

    public ThingErrorResponseAssert dittoRuntimeExceptionHasErrorCode(final String expectedErrorCode) {
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

    private <T> ThingErrorResponseAssert assertThatEquals(final T actual, final T expected, final String propertyName) {
        isNotNull();
        Assertions.assertThat(actual)
                .overridingErrorMessage("Expected Thing Error Response to have %s " + "\n<%s> but it had \n<%s>",
                        propertyName,
                        expected, actual)
                .isEqualTo(expected);
        return this;
    }

}
