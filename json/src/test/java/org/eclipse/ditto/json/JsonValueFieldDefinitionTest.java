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
 * Unit test for {@link org.eclipse.ditto.json.JsonValueFieldDefinition}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class JsonValueFieldDefinitionTest {

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
        assertInstancesOf(JsonValueFieldDefinition.class,
                areImmutable(),
                provided(JsonPointer.class, Function.class, JsonFieldMarker.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(JsonValueFieldDefinition.class)
                .withRedefinedSuperclass()
                .usingGetClass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateNewInstanceWithNullJsonPointer() {
        JsonValueFieldDefinition.newInstance(null, JsonArray.class, JsonValue::isArray, JsonValue::asArray,
                knownFieldMarkers);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateNewInstanceWithNullValueType() {
        JsonValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, null, JsonValue::isObject, JsonValue::asObject,
                knownFieldMarkers);
    }

    @Test
    public void getPointerReturnsExpected() {
        final JsonValueFieldDefinition underTest =
                JsonValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, JsonValue.class, JsonValue::isArray,
                        JsonValue::asArray, knownFieldMarkers);

        assertThat(underTest.getPointer()).isEqualTo(KNOWN_JSON_POINTER);
    }

    @Test
    public void getValueTypeReturnsExpected() {
        final Class<JsonObject> valueType = JsonObject.class;
        final JsonValueFieldDefinition underTest =
                JsonValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, valueType, JsonValue::isObject,
                        JsonValue::asObject, knownFieldMarkers);

        assertThat(Objects.equals(underTest.getValueType(), valueType)).isTrue();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getMarkersReturnsUnmodifiableSet() {
        final JsonValueFieldDefinition<JsonValue> underTest =
                JsonValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, JsonValue.class, JsonValue::isObject,
                        JsonValue::asObject, knownFieldMarkers);

        final Set<JsonFieldMarker> markers = underTest.getMarkers();
        markers.add(mock(JsonFieldMarker.class));
    }

    @Test(expected = NullPointerException.class)
    public void tryToInvokeIsMarkedWithOnlyNullMarker() {
        final JsonValueFieldDefinition<JsonArray> underTest =
                JsonValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, JsonArray.class, JsonValue::isArray,
                        JsonValue::asArray, knownFieldMarkers);

        underTest.isMarkedAs(null);
    }

    @Test
    public void isMarkedAsReturnsTrueIfDefinitionContainsAskedMarkers() {
        knownFieldMarkers = new JsonFieldMarker[] {schemaVersionMarkerMock, regularTypeMarkerMock};

        final JsonValueFieldDefinition<JsonObject> underTest =
                JsonValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, JsonObject.class, JsonValue::isObject,
                        JsonValue::asObject, knownFieldMarkers);

        assertThat(underTest.isMarkedAs(schemaVersionMarkerMock, regularTypeMarkerMock)).isTrue();
    }

    @Test
    public void isMarkedAsReturnsFalseIfDefinitionContainsNotAllAskedMarkers() {
        knownFieldMarkers = new JsonFieldMarker[] {schemaVersionMarkerMock, specialTypeMarkerMock};

        final JsonValueFieldDefinition<JsonArray> underTest =
                JsonValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, JsonArray.class, JsonValue::isArray,
                        JsonValue::asArray, knownFieldMarkers);

        assertThat(underTest.isMarkedAs(schemaVersionMarkerMock, regularTypeMarkerMock)).isFalse();
    }

    @Test
    public void isMarkedAsReturnsTrueIfDefinitionContainsAskedMarker() {
        knownFieldMarkers = new JsonFieldMarker[] {schemaVersionMarkerMock, regularTypeMarkerMock};

        final JsonValueFieldDefinition<JsonObject> underTest =
                JsonValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, JsonObject.class, JsonValue::isObject,
                        JsonValue::asObject, knownFieldMarkers);

        assertThat(underTest.isMarkedAs(regularTypeMarkerMock)).isTrue();
    }

    @Test
    public void stringRepresentationContainsExpected() {
        final JsonValueFieldDefinition<JsonArray> underTest =
                JsonValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, JsonArray.class, JsonValue::isArray,
                        JsonValue::asArray, knownFieldMarkers);

        assertThat(underTest.toString())
                .contains("pointer")
                .contains("valueType")
                .contains("JsonArray")
                .contains("checkJavaTypeFunction")
                .contains("mappingFunction")
                .contains("markers");
    }

    @Test
    public void tryToMapJavaNullValue() {
        final JsonValueFieldDefinition<JsonObject> underTest =
                JsonValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, JsonObject.class, JsonValue::isObject,
                        JsonValue::asObject);

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.mapValue(null))
                .withMessage("The %s must not be (Java) null!", "JsonValue to be mapped")
                .withNoCause();
    }

    @Test
    public void mapJsonNullLiteralWithJsonObjectFieldDefinitionReturnsExpected() {
        final JsonValue nullLiteral = JsonFactory.nullLiteral();

        final JsonValueFieldDefinition<JsonObject> underTest =
                JsonValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, JsonObject.class, JsonValue::isObject,
                        JsonValue::asObject);

        final JsonObject mapped = underTest.mapValue(nullLiteral);

        assertThat(mapped).isNullLiteral().isEmpty();
    }

    @Test
    public void mapJsonArrayWithJsonArrayObjectFieldDefinitionReturnsExpected() {
        final JsonValue sourceArray = JsonFactory.newArrayBuilder().add("foo", "bar").add(true).build();

        final JsonValueFieldDefinition<JsonArray> underTest =
                JsonValueFieldDefinition.newInstance(KNOWN_JSON_POINTER, JsonArray.class, JsonValue::isArray,
                        JsonValue::asArray);

        final JsonArray mapped = underTest.mapValue(sourceArray);

        assertThat(mapped).isEqualTo(sourceArray);
    }

}
