/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.eclipse.ditto.things.model.ThingTooLargeException;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyFeatureDesiredProperty}.
 */
public final class ModifyFeatureDesiredPropertyTest {

    private static final JsonPointer DESIRED_PROPERTY_POINTER =
            JsonFactory.newPointer("desiredProperties/foo");

    private static final JsonValue DESIRED_PROPERTY_VALUE = JsonFactory.newValue("bar");

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, ModifyFeatureDesiredProperty.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ModifyFeatureDesiredProperty.JSON_FEATURE_ID, TestConstants.Feature.HOVER_BOARD_ID)
            .set(ModifyFeatureDesiredProperty.JSON_DESIRED_PROPERTY, DESIRED_PROPERTY_POINTER.toString())
            .set(ModifyFeatureDesiredProperty.JSON_DESIRED_PROPERTY_VALUE, DESIRED_PROPERTY_VALUE)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyFeatureDesiredProperty.class,
                areImmutable(),
                provided(JsonPointer.class, JsonValue.class, ThingId.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyFeatureDesiredProperty.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = ThingIdInvalidException.class)
    public void tryToCreateInstanceWithNullThingIdString() {
        ModifyFeatureDesiredProperty.of(ThingId.of(null), TestConstants.Feature.HOVER_BOARD_ID,
                DESIRED_PROPERTY_POINTER,
                DESIRED_PROPERTY_VALUE,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullThingId() {
        ModifyFeatureDesiredProperty.of(null, TestConstants.Feature.HOVER_BOARD_ID, DESIRED_PROPERTY_POINTER,
                DESIRED_PROPERTY_VALUE,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullFeatureId() {
        ModifyFeatureDesiredProperty.of(TestConstants.Thing.THING_ID, null, DESIRED_PROPERTY_POINTER,
                DESIRED_PROPERTY_VALUE,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPropertyJsonPointer() {
        ModifyFeatureDesiredProperty.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.HOVER_BOARD_ID,
                null,
                DESIRED_PROPERTY_VALUE,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPropertyValue() {
        ModifyFeatureDesiredProperty.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.HOVER_BOARD_ID,
                DESIRED_PROPERTY_POINTER,
                null,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final ModifyFeatureDesiredProperty underTest =
                ModifyFeatureDesiredProperty.of(TestConstants.Thing.THING_ID,
                        TestConstants.Feature.HOVER_BOARD_ID,
                        DESIRED_PROPERTY_POINTER,
                        DESIRED_PROPERTY_VALUE,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void tryToCreateInstanceWithValidArguments() {
        ModifyFeatureDesiredProperty.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.HOVER_BOARD_ID,
                DESIRED_PROPERTY_POINTER,
                DESIRED_PROPERTY_VALUE,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void tryToCreateInstanceWithInvalidArguments() {
        ModifyFeatureDesiredProperty.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.HOVER_BOARD_ID,
                TestConstants.Pointer.INVALID_JSON_POINTER,
                DESIRED_PROPERTY_VALUE,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createInstanceFromValidJson() {
        final JsonObject invalidJson = KNOWN_JSON.toBuilder()
                .set(ModifyFeatureDesiredProperty.JSON_DESIRED_PROPERTY, TestConstants.Pointer.INVALID_JSON_POINTER.toString()).build();

        ModifyFeatureDesiredProperty.fromJson(invalidJson.toString(), TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createInstanceFromInvalidJsonValue() {
        final JsonValue invalid = JsonValue.of(JsonObject.of("{\"bar/baz\":false}"));

        ModifyFeatureDesiredProperty.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.HOVER_BOARD_ID,
                TestConstants.Pointer.VALID_JSON_POINTER,
                invalid,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void tryToCreateInstanceWithValidPropertyJsonObject() {
        final JsonValue valid = JsonValue.of(JsonObject.of("{\"bar.baz\":false}"));

        ModifyFeatureDesiredProperty.of(TestConstants.Thing.THING_ID,
                TestConstants.Feature.HOVER_BOARD_ID,
                TestConstants.Pointer.VALID_JSON_POINTER,
                valid,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void createInstanceFromJsonWithInvalidPropertyPath() {
        final ModifyFeatureDesiredProperty underTest =
                ModifyFeatureDesiredProperty.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        Assertions.assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getFeatureId()).isEqualTo(TestConstants.Feature.HOVER_BOARD_ID);
        assertThat(underTest.getDesiredPropertyPointer()).isEqualTo(DESIRED_PROPERTY_POINTER);
        assertThat(underTest.getDesiredPropertyValue()).isEqualTo(DESIRED_PROPERTY_VALUE);
    }

    @Test
    public void modifyTooLargeFeatureProperty() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TestConstants.THING_SIZE_LIMIT_BYTES; i++) {
            sb.append('a');
        }
        sb.append('b');

        assertThatThrownBy(() ->
                ModifyFeatureDesiredProperty.of(
                        ThingId.of("foo", "bar"),
                        "foo", JsonPointer.of("foo"),
                        JsonValue.of(sb.toString()),
                        DittoHeaders.empty()))
                .isInstanceOf(ThingTooLargeException.class);
    }

}
