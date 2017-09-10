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

import static org.eclipse.ditto.model.base.assertions.DittoBaseAssertions.assertThat;

import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.signals.base.JsonParsableRegistry;
import org.eclipse.ditto.signals.commands.base.CommonErrorRegistry;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayMethodNotAllowedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayServiceTimeoutException;
import org.junit.Test;


/**
 * Unit test for {@link CommonErrorRegistry}.
 */
public class CommonErrorRegistryTest {

    final JsonParsableRegistry<DittoRuntimeException> underTest = CommonErrorRegistry.newInstance();


    @Test
    public void parseJsonError() {
        final DittoRuntimeException error = new DittoJsonException(
                JsonMissingFieldException.newBuilder().fieldName("thingId").build(),
                TestConstants.DITTO_HEADERS);
        final JsonObject jsonObject = error.toJson(FieldType.regularOrSpecial());

        final DittoRuntimeException parsedError = underTest.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedError).isEqualTo(error);
    }


    @Test
    public void parseGatewayTimeoutError() {
        final GatewayServiceTimeoutException error =
                GatewayServiceTimeoutException.newBuilder().message("Gateway timeout")
                        .dittoHeaders(TestConstants.DITTO_HEADERS).build();
        final JsonObject jsonObject = error.toJson(FieldType.regularOrSpecial());

        final DittoRuntimeException parsedError = underTest.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedError).isEqualTo(error);
    }


    @Test
    public void parseGatewayMethodNotAllowedError() {
        final GatewayMethodNotAllowedException error =
                GatewayMethodNotAllowedException.newBuilder("PUT")
                        .dittoHeaders(TestConstants.DITTO_HEADERS).build();
        final JsonObject jsonObject = error.toJson(FieldType.regularOrSpecial());

        final DittoRuntimeException parsedError = underTest.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedError).isEqualTo(error);
    }


    @Test
    public void parseGatewayInternalError() {
        final GatewayInternalErrorException error =
                GatewayInternalErrorException.newBuilder().message("PUT is not allowed!")
                        .dittoHeaders(TestConstants.DITTO_HEADERS).build();
        final JsonObject jsonObject = error.toJson(FieldType.regularOrSpecial());

        final DittoRuntimeException parsedError = underTest.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedError).isEqualTo(error);
    }

}
