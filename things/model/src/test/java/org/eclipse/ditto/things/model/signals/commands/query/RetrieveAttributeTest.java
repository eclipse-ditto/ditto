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
package org.eclipse.ditto.things.model.signals.commands.query;

import static org.eclipse.ditto.things.model.signals.commands.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKeyInvalidException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveAttribute}.
 */
public final class RetrieveAttributeTest {

    private static final JsonPointer KNOWN_JSON_POINTER = JsonFactory.newPointer("key1");

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, RetrieveAttribute.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(RetrieveAttribute.JSON_ATTRIBUTE, KNOWN_JSON_POINTER.toString())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveAttribute.class, areImmutable(),
                provided(JsonPointer.class, ThingId.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveAttribute.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullThingId() {
        RetrieveAttribute.of(null, KNOWN_JSON_POINTER, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullJsonPointer() {
        RetrieveAttribute.of(TestConstants.Thing.THING_ID, null, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void tryToCreateInstanceWithValidArguments() {
        RetrieveAttribute.of(TestConstants.Thing.THING_ID, KNOWN_JSON_POINTER, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final RetrieveAttribute underTest =
                RetrieveAttribute.of(TestConstants.Thing.THING_ID, KNOWN_JSON_POINTER,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final RetrieveAttribute underTest =
                RetrieveAttribute.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        Assertions.assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getAttributePointer()).isEqualTo(KNOWN_JSON_POINTER);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createInstanceFromInvalidJson() {
        final JsonObject invalidJson = KNOWN_JSON.toBuilder()
                .set(RetrieveAttribute.JSON_ATTRIBUTE, TestConstants.Pointer.INVALID_JSON_POINTER.toString())
                .build();
        RetrieveAttribute.fromJson(invalidJson, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createInstanceFromInvalidArguments() {
        RetrieveAttribute.of(TestConstants.Thing.THING_ID, TestConstants.Pointer.INVALID_JSON_POINTER, TestConstants.EMPTY_DITTO_HEADERS);
    }
}
