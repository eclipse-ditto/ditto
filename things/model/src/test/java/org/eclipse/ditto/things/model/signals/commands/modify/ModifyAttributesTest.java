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
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.AttributesModelFactory;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingTooLargeException;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyAttributes}.
 */
public final class ModifyAttributesTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, ModifyAttributes.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ModifyAttributes.JSON_ATTRIBUTES, TestConstants.Thing.ATTRIBUTES)
            .build();

    private static final JsonObject JSON_WITH_NULL_ATTRIBUTE = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, ModifyAttributes.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ModifyAttributes.JSON_ATTRIBUTES, JsonFactory.nullObject())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyAttributes.class, areImmutable(),
                provided(Attributes.class, ThingId.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyAttributes.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullAttribute() {
        ModifyAttributes.of(TestConstants.Thing.THING_ID, null, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final ModifyAttributes underTest = ModifyAttributes.of(TestConstants.Thing.THING_ID,
                TestConstants.Thing.ATTRIBUTES, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ModifyAttributes underTest =
                ModifyAttributes.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        Assertions.assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        Assertions.assertThat(underTest.getAttributes()).isEqualTo(TestConstants.Thing.ATTRIBUTES);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createInstanceFromInvalidAttributePointer() {

        final Attributes attributesWithInvalidPointer =
                TestConstants.Thing.ATTRIBUTES.toBuilder().set("valid", JsonFactory.newObjectBuilder().set("invälid",
                        JsonValue.of(42)).build()).build();

        ModifyAttributes.fromJson(KNOWN_JSON.toBuilder()
                .set(ModifyAttributes.JSON_ATTRIBUTES, attributesWithInvalidPointer)
                .toString(), TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void createInstanceFromValidJsonWithNullAttribute() {
        final ModifyAttributes underTest =
                ModifyAttributes.fromJson(JSON_WITH_NULL_ATTRIBUTE.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        Assertions.assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        Assertions.assertThat(underTest.getAttributes()).isEqualTo(AttributesModelFactory.nullAttributes());
    }

    @Test
    public void modifyTooLargeAttributes() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TestConstants.THING_SIZE_LIMIT_BYTES; i++) {
            sb.append('a');
        }

        final JsonObject largeAttributes = JsonObject.newBuilder()
                .set("a", sb.toString())
                .build();

        assertThatThrownBy(() -> ModifyAttributes.of(ThingId.of("foo", "bar"),
                Attributes.newBuilder().set("a", largeAttributes).build(), DittoHeaders.empty()))
                .isInstanceOf(ThingTooLargeException.class);
    }

}
