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
package org.eclipse.ditto.things.model.signals.commands.modify;

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
import org.eclipse.ditto.things.model.signals.commands.exceptions.AttributePointerInvalidException;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DeleteAttribute}.
 */
public final class DeleteAttributeTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, DeleteAttribute.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(DeleteAttribute.JSON_ATTRIBUTE, TestConstants.Pointer.VALID_JSON_POINTER.toString())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(DeleteAttribute.class, areImmutable(),
                provided(JsonPointer.class, ThingId.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DeleteAttribute.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullThingId() {
        DeleteAttribute.of(null, TestConstants.Pointer.VALID_JSON_POINTER, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullJsonPointers() {
        DeleteAttribute.of(TestConstants.Thing.THING_ID, null, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void createInstanceWithValidArguments() {
        final DeleteAttribute underTest = DeleteAttribute.of(TestConstants.Thing.THING_ID,
                TestConstants.Pointer.VALID_JSON_POINTER, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getAttributePointer()).isEqualTo(TestConstants.Pointer.VALID_JSON_POINTER);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createInstanceWithInvalidArguments() {
        DeleteAttribute.of(TestConstants.Thing.THING_ID, TestConstants.Pointer.INVALID_JSON_POINTER,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = AttributePointerInvalidException.class)
    public void createInstanceWithEmptyPointer() {
        DeleteAttribute.of(TestConstants.Thing.THING_ID, TestConstants.Pointer.EMPTY_JSON_POINTER, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final DeleteAttribute underTest = DeleteAttribute.of(TestConstants.Thing.THING_ID,
                TestConstants.Pointer.VALID_JSON_POINTER, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final DeleteAttribute underTest =
                DeleteAttribute.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        Assertions.assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getAttributePointer()).isEqualTo(TestConstants.Pointer.VALID_JSON_POINTER);
    }

}
