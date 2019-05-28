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
package org.eclipse.ditto.signals.commands.thingsearch.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URI;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.signals.base.GlobalErrorRegistry;
import org.eclipse.ditto.signals.commands.thingsearch.query.TestConstants;
import org.junit.Test;

/**
 * Tests {@link InvalidOptionException}.
 */
public class InvalidOptionExceptionTest {

    private static final String EXPECTED_MESSAGE = TestConstants.KNOWN_INVALID_OPTION_EXCEPTION_MESSAGE;

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(DittoRuntimeException.JsonFields.STATUS, InvalidOptionException.STATUS_CODE.toInt())
            .set(DittoRuntimeException.JsonFields.ERROR_CODE, InvalidOptionException.ERROR_CODE)
            .set(DittoRuntimeException.JsonFields.MESSAGE,
                    TestConstants.INVALID_OPTION_EXCEPTION.getMessage())
            .set(DittoRuntimeException.JsonFields.DESCRIPTION,
                    TestConstants.INVALID_OPTION_EXCEPTION.getDescription().orElse(null),
                    JsonField.isValueNonNull())
            .set(DittoRuntimeException.JsonFields.HREF,
                    TestConstants.INVALID_OPTION_EXCEPTION.getHref().map(URI::toString).orElse(null),
                    JsonField.isValueNonNull())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(InvalidOptionException.class, areImmutable());
    }


    @Test
    public void toJsonReturnsExpected() {
        final JsonObject jsonObject = TestConstants.INVALID_OPTION_EXCEPTION.toJson();

        assertThat(jsonObject).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final InvalidOptionException underTest = InvalidOptionException.fromJson(KNOWN_JSON, TestConstants
                .EMPTY_DITTO_HEADERS);

        assertThat(underTest).isEqualTo(TestConstants.INVALID_OPTION_EXCEPTION);
    }


    @Test
    public void checkErrorCodeWorks() {
        final DittoRuntimeException actual =
                GlobalErrorRegistry.getInstance().parse(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(actual).isEqualTo(TestConstants.INVALID_OPTION_EXCEPTION);
    }


    @Test
    public void copy() {
        final DittoRuntimeException copy =
                DittoRuntimeException.newBuilder(TestConstants.INVALID_OPTION_EXCEPTION).build();
        assertThat(copy).isEqualTo(TestConstants.INVALID_OPTION_EXCEPTION);
    }


    @Test
    public void checkGetters() {
        Assertions.assertThat(TestConstants.INVALID_OPTION_EXCEPTION.getMessage()).isEqualTo(EXPECTED_MESSAGE);
        Assertions.assertThat(TestConstants.INVALID_OPTION_EXCEPTION.getStatusCode())
                .isEqualTo(InvalidOptionException.STATUS_CODE);
        Assertions.assertThat(TestConstants.INVALID_OPTION_EXCEPTION.getDescription().orElse(null)).isEqualTo(
                InvalidOptionException.DEFAULT_DESCRIPTION);
    }
}
