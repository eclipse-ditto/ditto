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
package org.eclipse.ditto.json;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

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

    private Set<JsonFieldMarker> knownFieldMarkers = null;


    @Before
    public void setUp() {
        knownFieldMarkers = new HashSet<>();
    }


    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonFieldDefinition.class, //
                areImmutable(), //
                provided(JsonPointer.class, JsonFieldMarker.class).areAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableJsonFieldDefinition.class) //
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateNewInstanceWithNullJsonPointer() {
        ImmutableJsonFieldDefinition.of(null, String.class, knownFieldMarkers);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateNewInstanceWithNullValueType() {
        ImmutableJsonFieldDefinition.of(KNOWN_JSON_POINTER, null, knownFieldMarkers);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateNewInstanceWithNullFieldMarkers() {
        ImmutableJsonFieldDefinition.of(KNOWN_JSON_POINTER, String.class, null);
    }


    @Test
    public void getPointerReturnsExpected() {
        final ImmutableJsonFieldDefinition underTest =
                ImmutableJsonFieldDefinition.of(KNOWN_JSON_POINTER, String.class, knownFieldMarkers);

        assertThat(underTest.getPointer()).isEqualTo(KNOWN_JSON_POINTER);
    }


    @Test
    public void getValueTypeReturnsExpected() {
        final Class<String> valueType = String.class;
        final ImmutableJsonFieldDefinition underTest =
                ImmutableJsonFieldDefinition.of(KNOWN_JSON_POINTER, valueType, knownFieldMarkers);

        assertThat(Objects.equals(underTest.getValueType(), valueType)).isTrue();
    }


    @Test(expected = UnsupportedOperationException.class)
    public void getMarkersReturnsUnmodifiableSet() {
        final JsonFieldDefinition underTest =
                ImmutableJsonFieldDefinition.of(KNOWN_JSON_POINTER, int.class, knownFieldMarkers);

        final Set<JsonFieldMarker> markers = underTest.getMarkers();
        markers.add(mock(JsonFieldMarker.class));
    }


    @Test(expected = NullPointerException.class)
    public void tryToInvokeIsMarkedWithOnlyNullMarker() {
        final JsonFieldDefinition underTest =
                ImmutableJsonFieldDefinition.of(KNOWN_JSON_POINTER, int.class, knownFieldMarkers);

        underTest.isMarkedAs(null);
    }


    @Test
    public void isMarkedAsReturnsTrueIfDefinitionContainsAskedMarkers() {
        knownFieldMarkers.add(schemaVersionMarkerMock);
        knownFieldMarkers.add(regularTypeMarkerMock);

        final JsonFieldDefinition underTest =
                ImmutableJsonFieldDefinition.of(KNOWN_JSON_POINTER, int.class, knownFieldMarkers);

        assertThat(underTest.isMarkedAs(schemaVersionMarkerMock, regularTypeMarkerMock)).isTrue();
    }


    @Test
    public void isMarkedAsReturnsFalseIfDefinitionContainsNotAllAskedMarkers() {
        knownFieldMarkers.add(schemaVersionMarkerMock);
        knownFieldMarkers.add(specialTypeMarkerMock);

        final JsonFieldDefinition underTest =
                ImmutableJsonFieldDefinition.of(KNOWN_JSON_POINTER, int.class, knownFieldMarkers);

        assertThat(underTest.isMarkedAs(schemaVersionMarkerMock, regularTypeMarkerMock)).isFalse();
    }


    @Test
    public void isMarkedAsReturnsTrueIfDefinitionContainsAskedMarker() {
        knownFieldMarkers.add(schemaVersionMarkerMock);
        knownFieldMarkers.add(regularTypeMarkerMock);

        final JsonFieldDefinition underTest =
                ImmutableJsonFieldDefinition.of(KNOWN_JSON_POINTER, int.class, knownFieldMarkers);

        assertThat(underTest.isMarkedAs(regularTypeMarkerMock)).isTrue();
    }


    @Test
    public void stringRespresentationContainsExpected() {
        final JsonFieldDefinition underTest =
                ImmutableJsonFieldDefinition.of(KNOWN_JSON_POINTER, double.class, knownFieldMarkers);

        assertThat(underTest.toString()) //
                .contains("pointer") //
                .contains("valueType") //
                .contains("double") //
                .contains("markers");
    }

}
