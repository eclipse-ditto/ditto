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
 * Unit test for {@link JavaValueFieldDefinition}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class JavaValueFieldDefinitionTest {

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
        assertInstancesOf(JavaValueFieldDefinition.class,
                areImmutable(),
                provided(JsonPointer.class, Function.class, JsonFieldMarker.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(JavaValueFieldDefinition.class)
                .withRedefinedSuperclass()
                .usingGetClass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateNewInstanceWithNullJsonPointer() {
        JavaValueFieldDefinition.newInstance(null, String.class, JsonValue::isString, JsonValue::asString,
                knownFieldMarkers);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateNewInstanceWithNullValueType() {
        JavaValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, null, JsonValue::isString, JsonValue::asString,
                knownFieldMarkers);
    }

    @Test
    public void getPointerReturnsExpected() {
        final JavaValueFieldDefinition underTest =
                JavaValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, String.class, JsonValue::isString,
                        JsonValue::asString, knownFieldMarkers);

        assertThat(underTest.getPointer()).isEqualTo(KNOWN_JSON_POINTER);
    }

    @Test
    public void getValueTypeReturnsExpected() {
        final Class<String> valueType = String.class;
        final JavaValueFieldDefinition underTest =
                JavaValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, valueType, JsonValue::isString,
                        JsonValue::asString, knownFieldMarkers);

        assertThat(Objects.equals(underTest.getValueType(), valueType)).isTrue();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getMarkersReturnsUnmodifiableSet() {
        final JavaValueFieldDefinition<Integer> underTest =
                JavaValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, int.class, JsonValue::isNumber,
                        JsonValue::asInt, knownFieldMarkers);

        final Set<JsonFieldMarker> markers = underTest.getMarkers();
        markers.add(mock(JsonFieldMarker.class));
    }

    @Test(expected = NullPointerException.class)
    public void tryToInvokeIsMarkedWithOnlyNullMarker() {
        final JavaValueFieldDefinition<Integer> underTest =
                JavaValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, int.class, JsonValue::isNumber,
                        JsonValue::asInt, knownFieldMarkers);

        underTest.isMarkedAs(null);
    }

    @Test
    public void isMarkedAsReturnsTrueIfDefinitionContainsAskedMarkers() {
        knownFieldMarkers = new JsonFieldMarker[] {schemaVersionMarkerMock, regularTypeMarkerMock};

        final JavaValueFieldDefinition<Integer> underTest =
                JavaValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, int.class, JsonValue::isNumber,
                        JsonValue::asInt, knownFieldMarkers);

        assertThat(underTest.isMarkedAs(schemaVersionMarkerMock, regularTypeMarkerMock)).isTrue();
    }

    @Test
    public void isMarkedAsReturnsFalseIfDefinitionContainsNotAllAskedMarkers() {
        knownFieldMarkers = new JsonFieldMarker[] {schemaVersionMarkerMock, specialTypeMarkerMock};

        final JavaValueFieldDefinition<Integer> underTest =
                JavaValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, int.class, JsonValue::isNumber,
                        JsonValue::asInt, knownFieldMarkers);

        assertThat(underTest.isMarkedAs(schemaVersionMarkerMock, regularTypeMarkerMock)).isFalse();
    }

    @Test
    public void isMarkedAsReturnsTrueIfDefinitionContainsAskedMarker() {
        knownFieldMarkers = new JsonFieldMarker[] {schemaVersionMarkerMock, regularTypeMarkerMock};

        final JavaValueFieldDefinition<Integer> underTest =
                JavaValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, int.class, JsonValue::isNumber,
                        JsonValue::asInt, knownFieldMarkers);

        assertThat(underTest.isMarkedAs(regularTypeMarkerMock)).isTrue();
    }

    @Test
    public void stringRepresentationContainsExpected() {
        final JavaValueFieldDefinition<Double> underTest =
                JavaValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, double.class, JsonValue::isNumber,
                        JsonValue::asDouble, knownFieldMarkers);

        assertThat(underTest.toString())
                .contains("pointer")
                .contains("valueType")
                .contains("double")
                .contains("checkJavaTypeFunction")
                .contains("mappingFunction")
                .contains("markers");
    }

    @Test
    public void tryToMapJavaNullValue() {
        final JavaValueFieldDefinition<String> underTest =
                JavaValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, String.class, JsonValue::isString,
                        JsonValue::asString);

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.mapValue(null))
                .withMessage("The %s must not be (Java) null!", "JsonValue to be mapped")
                .withNoCause();
    }

    @Test
    public void mapValueReturnsExpected() {
        final String stringValue = "Foo";
        final JsonValue jsonValue = JsonFactory.newValue(stringValue);

        final JavaValueFieldDefinition<String> underTest =
                JavaValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, String.class, JsonValue::isString,
                        JsonValue::asString);

        final String mapped = underTest.mapValue(jsonValue);

        assertThat(mapped).isEqualTo(stringValue);
    }

    @Test
    public void mapJsonNullLiteralWithStringFieldDefinition() {
        final Class<String> valueType = String.class;
        final JsonValue nullLiteral = JsonFactory.nullLiteral();

        final JavaValueFieldDefinition<String> underTest =
                JavaValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, valueType, JsonValue::isString,
                        JsonValue::asString);

        assertThat(underTest.mapValue(nullLiteral)).isNull();
    }

    @Test
    public void mapJsonNullLiteralWithIntFieldDefinition() {
        final Class<Integer> valueType = Integer.class;
        final JsonValue nullLiteral = JsonFactory.nullLiteral();

        final JavaValueFieldDefinition<Integer> underTest =
                JavaValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, valueType, JsonValue::isNumber,
                        JsonValue::asInt);

        assertThat(underTest.mapValue(nullLiteral)).isNull();
    }

    @Test
    public void tryToMapBooleanWithIntFieldDefinition() {
        final Class<Integer> valueType = Integer.class;
        final JsonValue boolJsonValue = JsonFactory.newValue(true);

        final JavaValueFieldDefinition<Integer> underTest =
                JavaValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, valueType, JsonValue::isNumber,
                        JsonValue::asInt);

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.mapValue(boolJsonValue))
                .withMessage("Value <%s> for <%s> is not of type <%s>!", boolJsonValue, KNOWN_JSON_POINTER,
                        valueType.getSimpleName())
                .withNoCause();
    }

    @Test
    public void mapIntWithIntFieldDefinitionReturnsExpected() {
        final int intValue = 42;
        final JsonValue intJsonValue = JsonFactory.newValue(intValue);

        final JavaValueFieldDefinition<Integer> underTest =
                JavaValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, Integer.class, JsonValue::isNumber,
                        JsonValue::asInt);

        final int mapped = underTest.mapValue(intJsonValue);

        assertThat(mapped).isEqualTo(intValue);
    }

}
