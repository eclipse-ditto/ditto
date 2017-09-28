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
package org.eclipse.ditto.protocoladapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutablePayload}.
 */
public final class ImmutablePayloadTest {

    private static final JsonPointer KNOWN_PATH = JsonPointer.empty();
    private static final JsonValue KNOWN_VALUE = JsonValue.of("foo");
    private static final HttpStatusCode KNOWN_STATUS = HttpStatusCode.OK;
    private static final long KNOWN_REVISION = 1337;
    private static final JsonFieldSelector KNOWN_FIELDS = JsonFieldSelector.newInstance("/foo");

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutablePayload.class)
                .usingGetClass()
                .verify();
    }

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutablePayload.class,
                areImmutable(),
                provided(JsonPointer.class, JsonValue.class, JsonFieldSelector.class).areAlsoImmutable());
    }

    /** */
    @Test
    public void jsonSerializationWorksAsExpected() {
        final JsonObject expected = JsonObject.newBuilder() //
                .set(Payload.JsonFields.PATH, KNOWN_PATH.toString()) //
                .set(Payload.JsonFields.VALUE, KNOWN_VALUE) //
                .set(Payload.JsonFields.STATUS, KNOWN_STATUS.toInt()) //
                .set(Payload.JsonFields.REVISION, KNOWN_REVISION) //
                .set(Payload.JsonFields.FIELDS, KNOWN_FIELDS.toString()) //
                .build();

        final ImmutablePayload payload =
                ImmutablePayload.of(KNOWN_PATH, KNOWN_VALUE, KNOWN_STATUS, KNOWN_REVISION, KNOWN_FIELDS);

        final JsonObject actual = payload.toJson();

        assertThat(actual).isEqualTo(expected);
    }

    /** */
    @Test
    public void jsonDeserializationWorksAsExpected() {
        final ImmutablePayload expected =
                ImmutablePayload.of(KNOWN_PATH, KNOWN_VALUE, KNOWN_STATUS, KNOWN_REVISION, KNOWN_FIELDS);

        final JsonObject payloadJsonObject = JsonObject.newBuilder() //
                .set(Payload.JsonFields.PATH, KNOWN_PATH.toString()) //
                .set(Payload.JsonFields.VALUE, KNOWN_VALUE) //
                .set(Payload.JsonFields.STATUS, KNOWN_STATUS.toInt()) //
                .set(Payload.JsonFields.REVISION, KNOWN_REVISION) //
                .set(Payload.JsonFields.FIELDS, KNOWN_FIELDS.toString()) //
                .build();

        final ImmutablePayload actual = ImmutablePayload.fromJson(payloadJsonObject);

        assertThat(actual).isEqualTo(expected);
    }

}
