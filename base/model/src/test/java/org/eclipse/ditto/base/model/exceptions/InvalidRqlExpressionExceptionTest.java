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
package org.eclipse.ditto.base.model.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URI;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.base.model.exceptions.InvalidRqlExpressionException}.
 */
public final class InvalidRqlExpressionExceptionTest {

    private static final String KNOWN_FILTER_STR = "eq(thingId,4711)";
    private static final String KNOWN_INVALID_FILTER_EXCEPTION_MESSAGE = "Invalid filter: " + KNOWN_FILTER_STR;
    private static final InvalidRqlExpressionException INVALID_FILTER_EXCEPTION =
            InvalidRqlExpressionException.newBuilder().message(KNOWN_INVALID_FILTER_EXCEPTION_MESSAGE).build();

    private static final String EXPECTED_MESSAGE = KNOWN_INVALID_FILTER_EXCEPTION_MESSAGE;

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(DittoRuntimeException.JsonFields.STATUS, InvalidRqlExpressionException.HTTP_STATUS.getCode())
            .set(DittoRuntimeException.JsonFields.ERROR_CODE, InvalidRqlExpressionException.ERROR_CODE)
            .set(DittoRuntimeException.JsonFields.MESSAGE, INVALID_FILTER_EXCEPTION.getMessage())
            .set(DittoRuntimeException.JsonFields.DESCRIPTION, INVALID_FILTER_EXCEPTION.getDescription().orElse(null),
                    JsonField.isValueNonNull())
            .set(DittoRuntimeException.JsonFields.HREF,
                    INVALID_FILTER_EXCEPTION.getHref().map(URI::toString).orElse(null), JsonField.isValueNonNull())
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
        final InvalidRqlExpressionException underTest =
                InvalidRqlExpressionException.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(underTest).isEqualTo(INVALID_FILTER_EXCEPTION);
    }

    @Test
    public void checkGetters() {
        assertThat(INVALID_FILTER_EXCEPTION.getMessage()).isEqualTo(EXPECTED_MESSAGE);
        assertThat(INVALID_FILTER_EXCEPTION.getHttpStatus()).isEqualTo(InvalidRqlExpressionException.HTTP_STATUS);
        assertThat(INVALID_FILTER_EXCEPTION.getDescription()).hasValue(
                InvalidRqlExpressionException.DEFAULT_DESCRIPTION);
    }

}
