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
package org.eclipse.ditto.signals.commands.things.modify;

import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit test for {@link ModifyAttribute}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ModifyAttributeTest {

    private static final JsonPointer KNOWN_JSON_POINTER = JsonFactory.newPointer("key1");

    private static final JsonValue KNOWN_ATTRIBUTE = JsonFactory.newValue("attribute");

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, ModifyAttribute.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID)
            .set(ModifyAttribute.JSON_ATTRIBUTE, KNOWN_JSON_POINTER.toString())
            .set(ModifyAttribute.JSON_ATTRIBUTE_VALUE, KNOWN_ATTRIBUTE)
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyAttribute.class,
                areImmutable(),
                provided(JsonPointer.class, JsonValue.class).areAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyAttribute.class)
                .withRedefinedSuperclass()
                .suppress(Warning.REFERENCE_EQUALITY)
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullAttribute() {
        ModifyAttribute.of(TestConstants.Thing.THING_ID, KNOWN_JSON_POINTER, null,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullJsonPointers() {
        ModifyAttribute.of(TestConstants.Thing.THING_ID, null, KNOWN_ATTRIBUTE,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void tryToCreateInstanceWithValidArguments() {
        ModifyAttribute.of(TestConstants.Thing.THING_ID, KNOWN_JSON_POINTER, KNOWN_ATTRIBUTE,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final ModifyAttribute underTest = ModifyAttribute.of(TestConstants.Thing.THING_ID,
                KNOWN_JSON_POINTER, KNOWN_ATTRIBUTE, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final ModifyAttribute underTest =
                ModifyAttribute.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getAttributePointer()).isEqualTo(KNOWN_JSON_POINTER);
        assertThat(underTest.getAttributeValue()).isEqualTo(KNOWN_ATTRIBUTE);
    }

}
