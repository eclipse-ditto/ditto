/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.things.modify;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.eclipse.ditto.model.things.ThingTooLargeException;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
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
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID)
            .set(ModifyFeatureProperty.JSON_FEATURE_ID, TestConstants.Feature.FLUX_CAPACITOR_ID)
            .set(ModifyFeatureProperty.JSON_PROPERTY, PROPERTY_JSON_POINTER.toString())
            .set(ModifyFeatureProperty.JSON_PROPERTY_VALUE, PROPERTY_VALUE)
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyFeatureProperty.class,
                areImmutable(),
                provided(JsonPointer.class, JsonValue.class).areAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyFeatureProperty.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = ThingIdInvalidException.class)
    public void tryToCreateInstanceWithNullThingId() {
        ModifyFeatureProperty.of(null, TestConstants.Feature.FLUX_CAPACITOR_ID, PROPERTY_JSON_POINTER, PROPERTY_VALUE,
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

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final ModifyFeatureProperty underTest =
                ModifyFeatureProperty.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getId()).isEqualTo(TestConstants.Thing.THING_ID);
        assertThat(underTest.getFeatureId()).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_ID);
        assertThat(underTest.getPropertyPointer()).isEqualTo(PROPERTY_JSON_POINTER);
        assertThat(underTest.getPropertyValue()).isEqualTo(PROPERTY_VALUE);
    }

    @Test
    public void modifyTooLargeFeatureProperty() {
        final StringBuilder sb = new StringBuilder();
        for(int i=0; i<TestConstants.THING_SIZE_LIMIT_BYTES; i++) {
            sb.append('a');
        }
        sb.append('b');

        assertThatThrownBy(() -> ModifyFeatureProperty.of("foo:bar", "foo", JsonPointer.of("foo"),
                JsonValue.of(sb.toString()), DittoHeaders.empty()))
                .isInstanceOf(ThingTooLargeException.class);
    }

}
