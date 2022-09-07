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
package org.eclipse.ditto.things.model.signals.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ThingErrorResponse}.
 */
public final class ThingErrorResponseTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommandResponse.JsonFields.TYPE, ThingErrorResponse.TYPE)
            .set(ThingCommandResponse.JsonFields.STATUS, HttpStatus.NOT_FOUND.getCode())
            .set(ThingCommandResponse.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ThingCommandResponse.JsonFields.PAYLOAD,
                    TestConstants.Thing.THING_NOT_ACCESSIBLE_EXCEPTION.toJson(FieldType.regularOrSpecial()))
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingErrorResponse.class,
                areImmutable(),
                provided(DittoRuntimeException.class, ThingId.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ThingErrorResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test
    public void toJsonReturnsExpected() {
        final ThingErrorResponse underTest =
                ThingErrorResponse.of(TestConstants.Thing.THING_ID, TestConstants.Thing.THING_NOT_ACCESSIBLE_EXCEPTION,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJsonCreated = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJsonCreated).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final ThingErrorResponse underTest =
                ThingErrorResponse.fromJson(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
    }

    @Test
    public void createInstanceFromUnregisteredException() {
        final JsonObject genericExceptionJson = KNOWN_JSON.toBuilder()
                .set(ThingCommandResponse.JsonFields.PAYLOAD, JsonObject.newBuilder()
                        .set(DittoRuntimeException.JsonFields.ERROR_CODE, "some.error")
                        .set(DittoRuntimeException.JsonFields.STATUS,
                                HttpStatus.VARIANT_ALSO_NEGOTIATES.getCode())
                        .set(DittoRuntimeException.JsonFields.DESCRIPTION, "the description")
                        .set(DittoRuntimeException.JsonFields.MESSAGE, "the message")
                        .build())
                .build();

        final ThingErrorResponse underTest =
                ThingErrorResponse.fromJson(genericExceptionJson, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getDittoRuntimeException().getErrorCode()).isEqualTo("some.error");
        assertThat(underTest.getDittoRuntimeException().getDescription()).contains("the description");
        assertThat(underTest.getDittoRuntimeException().getMessage()).isEqualTo("the message");
        assertThat(underTest.getDittoRuntimeException().getHttpStatus()).isEqualTo(HttpStatus.VARIANT_ALSO_NEGOTIATES);
        assertThat(underTest.getHttpStatus()).isEqualTo(HttpStatus.VARIANT_ALSO_NEGOTIATES);
    }

}
