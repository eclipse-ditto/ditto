/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableJsonFieldDefinition}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ImmutableJsonFieldDefinitionTest {

    private static final JsonPointer KNOWN_JSON_POINTER = JsonFactory.newPointer("root/sub/subsub");

    @Mock
    private JsonFieldMarker schemaVersionMarkerMock;

    @Mock
    private JsonFieldMarker regularTypeMarkerMock;

    @Mock
    private JsonFieldMarker specialTypeMarkerMock;

    private JsonFieldMarker[] knownFieldMarkers = null;


    @Before
    public void setUp() {
        knownFieldMarkers = new JsonFieldMarker[0];
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonFieldDefinition.class,
                areImmutable(),
                provided(JsonPointer.class, Function.class, JsonFieldMarker.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableJsonFieldDefinition.class)
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateNewInstanceWithNullJsonPointer() {
        ImmutableJsonFieldDefinition.newInstance(null, String.class, JsonValue::asString, knownFieldMarkers);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateNewInstanceWithNullValueType() {
        ImmutableJsonFieldDefinition.newInstance(KNOWN_JSON_POINTER, null, JsonValue::asString, knownFieldMarkers);
    }

    @Test
    public void getPointerReturnsExpected() {
        final ImmutableJsonFieldDefinition underTest =
                ImmutableJsonFieldDefinition.newInstance(KNOWN_JSON_POINTER, String.class, JsonValue::asString,
                        knownFieldMarkers);

        assertThat(underTest.getPointer()).isEqualTo(KNOWN_JSON_POINTER);
    }

    @Test
    public void getValueTypeReturnsExpected() {
        final Class<String> valueType = String.class;
        final ImmutableJsonFieldDefinition underTest =
                ImmutableJsonFieldDefinition.newInstance(KNOWN_JSON_POINTER, valueType, JsonValue::asString,
                        knownFieldMarkers);

        assertThat(Objects.equals(underTest.getValueType(), valueType)).isTrue();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getMarkersReturnsUnmodifiableSet() {
        final ImmutableJsonFieldDefinition<Integer> underTest =
                ImmutableJsonFieldDefinition.newInstance(KNOWN_JSON_POINTER, int.class, JsonValue::asInt,
                        knownFieldMarkers);

        final Set<JsonFieldMarker> markers = underTest.getMarkers();
        markers.add(mock(JsonFieldMarker.class));
    }

    @Test(expected = NullPointerException.class)
    public void tryToInvokeIsMarkedWithOnlyNullMarker() {
        final ImmutableJsonFieldDefinition<Integer> underTest =
                ImmutableJsonFieldDefinition.newInstance(KNOWN_JSON_POINTER, int.class, JsonValue::asInt,
                        knownFieldMarkers);

        underTest.isMarkedAs(null);
    }

    @Test
    public void isMarkedAsReturnsTrueIfDefinitionContainsAskedMarkers() {
        knownFieldMarkers = new JsonFieldMarker[] {schemaVersionMarkerMock, regularTypeMarkerMock};

        final ImmutableJsonFieldDefinition<Integer> underTest =
                ImmutableJsonFieldDefinition.newInstance(KNOWN_JSON_POINTER, int.class, JsonValue::asInt,
                        knownFieldMarkers);

        assertThat(underTest.isMarkedAs(schemaVersionMarkerMock, regularTypeMarkerMock)).isTrue();
    }

    @Test
    public void isMarkedAsReturnsFalseIfDefinitionContainsNotAllAskedMarkers() {
        knownFieldMarkers = new JsonFieldMarker[] {schemaVersionMarkerMock, specialTypeMarkerMock};

        final ImmutableJsonFieldDefinition<Integer> underTest =
                ImmutableJsonFieldDefinition.newInstance(KNOWN_JSON_POINTER, int.class, JsonValue::asInt,
                        knownFieldMarkers);

        assertThat(underTest.isMarkedAs(schemaVersionMarkerMock, regularTypeMarkerMock)).isFalse();
    }

    @Test
    public void isMarkedAsReturnsTrueIfDefinitionContainsAskedMarker() {
        knownFieldMarkers = new JsonFieldMarker[] {schemaVersionMarkerMock, regularTypeMarkerMock};

        final ImmutableJsonFieldDefinition<Integer> underTest =
                ImmutableJsonFieldDefinition.newInstance(KNOWN_JSON_POINTER, int.class, JsonValue::asInt,
                        knownFieldMarkers);

        assertThat(underTest.isMarkedAs(regularTypeMarkerMock)).isTrue();
    }

    @Test
    public void stringRepresentationContainsExpected() {
        final ImmutableJsonFieldDefinition<Double> underTest =
                ImmutableJsonFieldDefinition.newInstance(KNOWN_JSON_POINTER, double.class, JsonValue::asDouble,
                        knownFieldMarkers);

        assertThat(underTest.toString())
                .contains("pointer")
                .contains("valueType")
                .contains("double")
                .contains("markers");
    }

    @Test
    public void tryToMapJavaNullValue() {
        final ImmutableJsonFieldDefinition<String> underTest =
                ImmutableJsonFieldDefinition.newInstance(KNOWN_JSON_POINTER, String.class, JsonValue::asString);

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.mapValue(null))
                .withMessage("The %s must not be null!", "JsonValue to be mapped")
                .withNoCause();
    }

    @Test
    public void mapValueReturnsExpected() {
        final String stringValue = "Foo";
        final JsonValue jsonValue = JsonFactory.newValue(stringValue);

        final ImmutableJsonFieldDefinition<String> underTest =
                ImmutableJsonFieldDefinition.newInstance(KNOWN_JSON_POINTER, String.class, JsonValue::asString);

        final String mapped = underTest.mapValue(jsonValue);

        assertThat(mapped).isEqualTo(stringValue);
    }

    @Test
    public void tryToMapJsonNullLiteralWithStringFieldDefinition() {
        final Class<String> valueType = String.class;
        final JsonValue nullLiteral = JsonFactory.nullLiteral();

        final ImmutableJsonFieldDefinition<String> underTest =
                ImmutableJsonFieldDefinition.newInstance(KNOWN_JSON_POINTER, valueType, JsonValue::asString);

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.mapValue(nullLiteral))
                .withMessage("Value <%s> for <%s> is not of type <%s>!", nullLiteral, KNOWN_JSON_POINTER,
                        valueType.getSimpleName())
                .withNoCause();
    }

    @Test
    public void tryToMapJsonNullLiteralWithIntFieldDefinition() {
        final Class<Integer> valueType = Integer.class;
        final JsonValue nullLiteral = JsonFactory.nullLiteral();

        final ImmutableJsonFieldDefinition<Integer> underTest =
                ImmutableJsonFieldDefinition.newInstance(KNOWN_JSON_POINTER, valueType, JsonValue::asInt);

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.mapValue(nullLiteral))
                .withMessage("Value <%s> for <%s> is not of type <%s>!", nullLiteral, KNOWN_JSON_POINTER,
                        valueType.getSimpleName())
                .withNoCause();
    }

    @Test
    public void tryToMapBooleanWithIntFieldDefinition() {
        final Class<Integer> valueType = Integer.class;
        final JsonValue boolJsonValue = JsonFactory.newValue(true);

        final ImmutableJsonFieldDefinition<Integer> underTest =
                ImmutableJsonFieldDefinition.newInstance(KNOWN_JSON_POINTER, valueType, JsonValue::asInt);

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.mapValue(boolJsonValue))
                .withMessage("Value <%s> for <%s> is not of type <%s>!", boolJsonValue, KNOWN_JSON_POINTER,
                        valueType.getSimpleName())
                .withNoCause();
    }

    @Test
    public void mapJsonNullLiteralWithJsonObjectFieldDefinitionReturnsExpected() {
        final JsonValue nullLiteral = JsonFactory.nullLiteral();

        final ImmutableJsonFieldDefinition<JsonObject> underTest =
                ImmutableJsonFieldDefinition.newInstance(KNOWN_JSON_POINTER, JsonObject.class, JsonValue::asObject);

        final JsonObject mapped = underTest.mapValue(nullLiteral);

        assertThat(mapped).isNullLiteral().isEmpty();
    }

    @Test
    public void mapJsonArrayWithJsonArrayObjectFieldDefinitionReturnsExpected() {
        final JsonValue sourceArray = JsonFactory.newArrayBuilder().add("foo", "bar").add(true).build();

        final ImmutableJsonFieldDefinition<JsonArray> underTest =
                ImmutableJsonFieldDefinition.newInstance(KNOWN_JSON_POINTER, JsonArray.class, JsonValue::asArray);

        final JsonArray mapped = underTest.mapValue(sourceArray);

        assertThat(mapped).isEqualTo(sourceArray);
    }

    @Test
    public void mapIntWithIntFieldDefinitionReturnsExpected() {
        final int intValue = 42;
        final JsonValue intJsonValue = JsonFactory.newValue(intValue);

        final ImmutableJsonFieldDefinition<Integer> underTest =
                ImmutableJsonFieldDefinition.newInstance(KNOWN_JSON_POINTER, Integer.class, JsonValue::asInt);

        final int mapped = underTest.mapValue(intJsonValue);

        assertThat(mapped).isEqualTo(intValue);
    }

}
