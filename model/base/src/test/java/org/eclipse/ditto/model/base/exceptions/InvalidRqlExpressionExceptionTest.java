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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URI;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.junit.Test;

/**
 * Tests {@link InvalidRqlExpressionException}.
 */
public class InvalidRqlExpressionExceptionTest {

    private static final String KNOWN_FILTER_STR = "eq(thingId,4711)";
    private static final String KNOWN_INVALID_FILTER_EXCEPTION_MESSAGE = "Invalid filter: " + KNOWN_FILTER_STR;
    private static final InvalidRqlExpressionException INVALID_FILTER_EXCEPTION =
            InvalidRqlExpressionException
                    .newBuilder().message(KNOWN_INVALID_FILTER_EXCEPTION_MESSAGE).build();

    private static final String EXPECTED_MESSAGE = KNOWN_INVALID_FILTER_EXCEPTION_MESSAGE;

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(DittoRuntimeException.JsonFields.STATUS, InvalidRqlExpressionException.STATUS_CODE.toInt())
            .set(DittoRuntimeException.JsonFields.ERROR_CODE, InvalidRqlExpressionException.ERROR_CODE)
            .set(DittoRuntimeException.JsonFields.MESSAGE,
                    INVALID_FILTER_EXCEPTION.getMessage())
            .set(DittoRuntimeException.JsonFields.DESCRIPTION,
                    INVALID_FILTER_EXCEPTION.getDescription().orElse(null),
                    JsonField.isValueNonNull())
            .set(DittoRuntimeException.JsonFields.HREF,
                    INVALID_FILTER_EXCEPTION.getHref().map(URI::toString).orElse(null),
                    JsonField.isValueNonNull())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(InvalidRqlExpressionException.class, areImmutable());
    }


    @Test
    public void toJsonReturnsExpected() {
        final JsonObject jsonObject = INVALID_FILTER_EXCEPTION.toJson();

        assertThat(jsonObject).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final InvalidRqlExpressionException underTest = InvalidRqlExpressionException.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(underTest).isEqualTo(INVALID_FILTER_EXCEPTION);
    }



    @Test
    public void copy() {
        final DittoRuntimeException copy =
                DittoRuntimeException.newBuilder(INVALID_FILTER_EXCEPTION).build();
        assertThat(copy).isEqualTo(INVALID_FILTER_EXCEPTION);
    }


    @Test
    public void checkGetters() {
        Assertions.assertThat(INVALID_FILTER_EXCEPTION.getMessage()).isEqualTo(EXPECTED_MESSAGE);
        Assertions.assertThat(INVALID_FILTER_EXCEPTION.getStatusCode())
                .isEqualTo(InvalidRqlExpressionException.STATUS_CODE);
        Assertions.assertThat(INVALID_FILTER_EXCEPTION.getDescription().orElse(null)).isEqualTo(
                InvalidRqlExpressionException.DEFAULT_DESCRIPTION);
    }
}
