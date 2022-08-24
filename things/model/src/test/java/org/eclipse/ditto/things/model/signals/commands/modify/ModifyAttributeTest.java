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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.ditto.things.model.signals.commands.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKeyInvalidException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingTooLargeException;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.exceptions.AttributePointerInvalidException;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit test for {@link ModifyAttribute}.
 */
public final class ModifyAttributeTest {

    private static final JsonPointer KNOWN_JSON_POINTER = JsonFactory.newPointer("key1");

    private static final JsonValue KNOWN_ATTRIBUTE = JsonFactory.newValue("attribute");

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, ModifyAttribute.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ModifyAttribute.JSON_ATTRIBUTE, KNOWN_JSON_POINTER.toString())
            .set(ModifyAttribute.JSON_ATTRIBUTE_VALUE, KNOWN_ATTRIBUTE)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyAttribute.class,
                areImmutable(),
                provided(JsonPointer.class, JsonValue.class, ThingId.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyAttribute.class)
                .withRedefinedSuperclass()
                .suppress(Warning.REFERENCE_EQUALITY)
                .verify();
    }

    @Test
    public void tryToCreateInstanceWithNullAttribute() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ModifyAttribute.of(TestConstants.Thing.THING_ID, KNOWN_JSON_POINTER, null,
                        TestConstants.EMPTY_DITTO_HEADERS))
                .withNoCause();
    }

    @Test
    public void tryToCreateInstanceWithNullJsonPointer() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ModifyAttribute.of(TestConstants.Thing.THING_ID, null, KNOWN_ATTRIBUTE,
                        TestConstants.EMPTY_DITTO_HEADERS))
                .withNoCause();

    }

    @Test
    public void tryToCreateInstanceWithEmptyJsonPointer() {
        assertThatExceptionOfType(AttributePointerInvalidException.class)
                .isThrownBy(() -> ModifyAttribute.of(TestConstants.Thing.THING_ID, JsonPointer.empty(), KNOWN_ATTRIBUTE,
                        TestConstants.EMPTY_DITTO_HEADERS))
                .withNoCause();
    }

    @Test()
    public void tryToCreateInstanceWithValidArguments() {
        ModifyAttribute.of(TestConstants.Thing.THING_ID, TestConstants.Pointer.VALID_JSON_POINTER, KNOWN_ATTRIBUTE,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void tryToCreateInstanceWithInvalidAttributePointer() {
        ModifyAttribute.of(TestConstants.Thing.THING_ID, TestConstants.Pointer.INVALID_JSON_POINTER, KNOWN_ATTRIBUTE,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createInstanceFromInvalidJsonValue() {
        final JsonValue invalid = JsonValue.of(JsonObject.of("{\"bar/baz\":false}"));

        ModifyAttribute.of(TestConstants.Thing.THING_ID, TestConstants.Pointer.VALID_JSON_POINTER,
                invalid, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void tryToCreateInstanceWithValidJsonObjectAsValue() {
        final JsonValue valid = JsonValue.of(JsonObject.of("{\"bar.baz\":false}"));

        ModifyAttribute.of(TestConstants.Thing.THING_ID, TestConstants.Pointer.VALID_JSON_POINTER,
                valid, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = AttributePointerInvalidException.class)
    public void createInstanceWithEmptyPointer() {
        ModifyAttribute.of(TestConstants.Thing.THING_ID, TestConstants.Pointer.EMPTY_JSON_POINTER, KNOWN_ATTRIBUTE,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final ModifyAttribute underTest = ModifyAttribute.of(TestConstants.Thing.THING_ID,
                KNOWN_JSON_POINTER, KNOWN_ATTRIBUTE, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ModifyAttribute underTest =
                ModifyAttribute.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        Assertions.assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getAttributePointer()).isEqualTo(KNOWN_JSON_POINTER);
        assertThat(underTest.getAttributeValue()).isEqualTo(KNOWN_ATTRIBUTE);
    }

    @Test
    public void modifyTooLargeAttribute() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TestConstants.THING_SIZE_LIMIT_BYTES; i++) {
            sb.append('a');
        }
        sb.append('b');

        assertThatThrownBy(() -> ModifyAttribute.of(ThingId.of("foo", "bar"), JsonPointer.of("foo"),
                JsonValue.of(sb.toString()), DittoHeaders.empty()))
                .isInstanceOf(ThingTooLargeException.class);
    }

}
