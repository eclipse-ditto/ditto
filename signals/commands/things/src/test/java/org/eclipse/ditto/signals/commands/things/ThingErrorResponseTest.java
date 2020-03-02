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
package org.eclipse.ditto.signals.commands.things;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.ThingId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ThingErrorResponse}.
 */
public final class ThingErrorResponseTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommandResponse.JsonFields.TYPE, ThingErrorResponse.TYPE)
            .set(ThingCommandResponse.JsonFields.STATUS, HttpStatusCode.NOT_FOUND.toInt())
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
                .set(ThingCommandResponse.JsonFields.PAYLOAD,
                        DittoRuntimeException
                                .newBuilder("some.error", HttpStatusCode.VARIANT_ALSO_NEGOTIATES)
                                .description("the description")
                                .message("the message")
                                .build().toJson(FieldType.regularOrSpecial()))
                .build();

        final ThingErrorResponse underTest =
                ThingErrorResponse.fromJson(genericExceptionJson, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        Assertions.assertThat(underTest.getDittoRuntimeException()).isNotNull();
        Assertions.assertThat(underTest.getDittoRuntimeException().getErrorCode()).isEqualTo("some.error");
        Assertions.assertThat(underTest.getDittoRuntimeException().getDescription()).contains("the description");
        Assertions.assertThat(underTest.getDittoRuntimeException().getMessage()).isEqualTo("the message");
        Assertions.assertThat(underTest.getDittoRuntimeException().getStatusCode())
                .isEqualTo(HttpStatusCode.VARIANT_ALSO_NEGOTIATES);
        Assertions.assertThat(underTest.getStatusCode()).isEqualTo(HttpStatusCode.VARIANT_ALSO_NEGOTIATES);
    }
}
