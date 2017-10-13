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
package org.eclipse.ditto.signals.commands.live.assertions;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.assertions.AbstractCommandResponseAssert;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;

/**
 * An assert for {@link ThingErrorResponse}.
 */
public final class ThingErrorResponseAssert extends AbstractCommandResponseAssert<ThingErrorResponseAssert,
        ThingErrorResponse> {

    public ThingErrorResponseAssert(final ThingErrorResponse actual) {
        super(actual, ThingErrorResponseAssert.class);
    }

    public ThingErrorResponseAssert withType(final CharSequence expectedType) {
        return hasType(expectedType);
    }

    public ThingErrorResponseAssert withDittoHeaders(final DittoHeaders expectedDittoHeaders) {
        return hasDittoHeaders(expectedDittoHeaders);
    }

    public ThingErrorResponseAssert withStatus(final HttpStatusCode expectedStatus) {
        return hasStatus(expectedStatus);
    }

    public ThingErrorResponseAssert hasStatusCode(final HttpStatusCode expectedStatusCode) {
        return assertThatEquals(actual.getStatusCode(), expectedStatusCode, "HTTP status code");
    }

    public ThingErrorResponseAssert withDittoRuntimeException(
            final DittoRuntimeException expectedDittoRuntimeException) {
        return assertThatEquals(actual.getDittoRuntimeException(), expectedDittoRuntimeException,
                "DittoRuntimeException");
    }

    public ThingErrorResponseAssert withDittoRuntimeExceptionOfType(
            final Class<? extends DittoRuntimeException> expectedType) {
        isNotNull();
        final DittoRuntimeException DittoRuntimeException = actual.getDittoRuntimeException();
        Assertions.assertThat(DittoRuntimeException)
                .overridingErrorMessage(
                        "Expected DittoRuntimeException of ThingErrorResponse is of type\n<%s> but it " +
                                "was\n<%s>", expectedType.getSimpleName(),
                        DittoRuntimeException.getClass().getSimpleName())
                .isInstanceOf(expectedType);
        return myself;
    }

    private <T> ThingErrorResponseAssert assertThatEquals(final T actual, final T expected, final String propertyName) {
        isNotNull();
        Assertions.assertThat(actual) //
                .overridingErrorMessage("Expected ThingErrorResponse to have %s " + "\n<%s> but it had\n<%s>",
                        propertyName, expected, actual) //
                .isEqualTo(expected);
        return this;
    }

}
