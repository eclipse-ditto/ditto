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
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingTooLargeException;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyFeatureProperty}.
 */
public final class ModifyFeaturePropertyTest {

    private static final JsonPointer PROPERTY_JSON_POINTER = JsonFactory.newPointer("properties/foo");

    private static final JsonValue PROPERTY_VALUE = JsonFactory.newValue("bar");

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, ModifyFeatureProperty.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ModifyFeatureProperty.JSON_FEATURE_ID, TestConstants.Feature.FLUX_CAPACITOR_ID)
            .set(ModifyFeatureProperty.JSON_PROPERTY, PROPERTY_JSON_POINTER.toString())
            .set(ModifyFeatureProperty.JSON_PROPERTY_VALUE, PROPERTY_VALUE)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyFeatureProperty.class,
                areImmutable(),
                provided(JsonPointer.class, JsonValue.class, ThingId.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyFeatureProperty.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullThingId() {
        ModifyFeatureProperty.of(null, TestConstants.Feature.FLUX_CAPACITOR_ID, PROPERTY_JSON_POINTER,
                PROPERTY_VALUE,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullFeatureId() {
        ModifyFeatureProperty.of(TestConstants.Thing.THING_ID, null, PROPERTY_JSON_POINTER, PROPERTY_VALUE,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPropertyJsonPointer() {
        ModifyFeatureProperty.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID, null,
                PROPERTY_VALUE,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPropertyValue() {
        ModifyFeatureProperty.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                PROPERTY_JSON_POINTER, null,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final ModifyFeatureProperty underTest =
                ModifyFeatureProperty.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                        PROPERTY_JSON_POINTER, PROPERTY_VALUE, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void tryToCreateInstanceWithValidArguments() {
        ModifyFeatureProperty.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                PROPERTY_JSON_POINTER, PROPERTY_VALUE, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void tryToCreateInstanceWithInvalidArguments() {
        ModifyFeatureProperty.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                TestConstants.Pointer.INVALID_JSON_POINTER, PROPERTY_VALUE, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createInstanceFromValidJson() {
        final JsonObject invalidJson = KNOWN_JSON.toBuilder()
                .set(ModifyFeatureProperty.JSON_PROPERTY, TestConstants.Pointer.INVALID_JSON_POINTER.toString()).build();

        ModifyFeatureProperty.fromJson(invalidJson.toString(), TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createInstanceFromInvalidJsonValue() {
        final JsonValue invalid = JsonValue.of(JsonObject.of("{\"bar/baz\":false}"));

        ModifyFeatureProperty.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                TestConstants.Pointer.VALID_JSON_POINTER, invalid, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void tryToCreateInstanceWithValidPropertyJsonObject() {
        final JsonValue valid = JsonValue.of(JsonObject.of("{\"bar.baz\":false}"));

        ModifyFeatureProperty.of(TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                TestConstants.Pointer.VALID_JSON_POINTER, valid, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void createInstanceFromJsonWithInvalidPropertyPath() {
        final ModifyFeatureProperty underTest =
                ModifyFeatureProperty.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        Assertions.assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getFeatureId()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_ID);
        assertThat(underTest.getPropertyPointer()).isEqualTo(PROPERTY_JSON_POINTER);
        assertThat(underTest.getPropertyValue()).isEqualTo(PROPERTY_VALUE);
    }

    @Test
    public void modifyTooLargeFeatureProperty() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TestConstants.THING_SIZE_LIMIT_BYTES; i++) {
            sb.append('a');
        }
        sb.append('b');

        assertThatThrownBy(() -> ModifyFeatureProperty.of(ThingId.of("foo", "bar"), "foo", JsonPointer.of("foo"),
                JsonValue.of(sb.toString()), DittoHeaders.empty()))
                .isInstanceOf(ThingTooLargeException.class);
    }

}
