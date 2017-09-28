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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableJsonField}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ImmutableJsonFieldTest {

    @Mock
    private JsonValue jsonValueMock;

    @Mock
    private JsonKey jsonKeyMock;

    @Mock
    private JsonFieldDefinition fieldDefinitionMock;

    @Mock
    private JsonFieldMarker schemaVersionMarkerMock;

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonField.class,
                areImmutable(),
                provided(JsonKey.class, JsonValue.class, JsonField.class,
                        JsonFieldDefinition.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableJsonField.class).usingGetClass().verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullKey() {
        ImmutableJsonField.newInstance(null, jsonValueMock);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullValue() {
        ImmutableJsonField.newInstance(jsonKeyMock, null);
    }

    @Test
    public void getKeyNameReturnsExpected() {
        final String keyName = "key";
        when(jsonKeyMock.toString()).thenReturn(keyName);
        final JsonField underTest = ImmutableJsonField.newInstance(jsonKeyMock, jsonValueMock);

        assertThat(underTest.getKeyName()).isEqualTo(keyName);
    }

    @Test
    public void twoHashSetsWithSameJsonFieldsAreEqual() {
        final JsonKey fooKey = JsonFactory.newKey("foo");
        final JsonValue fooValue = JsonFactory.newValue("bar");
        final JsonKey pingKey = JsonFactory.newKey("ping");
        final JsonValue pingValue = JsonFactory.newValue("pong");

        final Set<JsonField> left =
                new HashSet<>(Arrays.asList(
                        JsonFactory.newField(fooKey, fooValue), JsonFactory.newField(pingKey, pingValue)));
        final Set<JsonField> right =
                new HashSet<>(Arrays.asList(
                        JsonFactory.newField(pingKey, pingValue), JsonFactory.newField(fooKey, fooValue)));

        assertThat(left).isEqualTo(right);
    }

    @Test
    public void fieldHasNoDefinitionByDefault() {
        final JsonField underTest = ImmutableJsonField.newInstance(jsonKeyMock, jsonValueMock);

        assertThat(underTest.getDefinition()).isEmpty();
    }

    @Test
    public void getDefinitionReturnsExpected() {
        final JsonField underTest = ImmutableJsonField.newInstance(jsonKeyMock, jsonValueMock, fieldDefinitionMock);

        assertThat(underTest.getDefinition()).contains(fieldDefinitionMock);
    }

    @Test
    public void fieldWithoutDefinitionIsNotMarkedAtAll() {
        final JsonField underTest = ImmutableJsonField.newInstance(jsonKeyMock, jsonValueMock);

        assertThat(underTest.isMarkedAs(schemaVersionMarkerMock)).isFalse();
    }

    @Test
    public void fieldWithSchemaVersionMarkerIsMarkedCorrectly() {
        when(fieldDefinitionMock.isMarkedAs(eq(schemaVersionMarkerMock))).thenReturn(true);
        final JsonField underTest = ImmutableJsonField.newInstance(jsonKeyMock, jsonValueMock, fieldDefinitionMock);

        assertThat(underTest.isMarkedAs(schemaVersionMarkerMock)).isTrue();
    }

    @Test
    public void stringRepresentationContainsExpectedWords() {
        final JsonField underTest = ImmutableJsonField.newInstance(jsonKeyMock, jsonValueMock, fieldDefinitionMock);

        assertThat(underTest.toString()).contains("key").contains("value").contains("definition");
    }

    @Test
    public void createInstanceViaInterfaceReturnsExpected() {
        final JsonValue jsonValue = JsonFactory.newValue(3);
        final JsonKey expectedKey = JsonFactory.newKey("/foo/bar/baz");
        final JsonField underTest = JsonField.newInstance(JsonFactory.newPointer("/foo/bar/baz"), jsonValue);

        assertThat(underTest.getKey()).isEqualTo(expectedKey);
        assertThat(underTest.getValue()).isEqualTo(jsonValue);
    }

}
