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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.json.JsonPatch.Operation.ADD;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableJsonPatch}.
 */
public final class ImmutableJsonPatchTest {

    private static final JsonPatch.Operation OPERATION = ADD;
    private static final JsonPointer PATH = JsonFactory.newPointer("new");
    private static final JsonValue VALUE = JsonFactory.newValue(2);

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonPatch.class,
                areImmutable(),
                provided(JsonPointer.class, JsonValue.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableJsonPatch.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void createNewInstanceReturnsExpected() {
        final ImmutableJsonPatch jsonPatch = ImmutableJsonPatch.newInstance(OPERATION, PATH, VALUE);

        assertThat(jsonPatch.getOperation()).isEqualTo(OPERATION);
        assertThat(jsonPatch.getPath()).isEqualTo(PATH);
        assertThat(jsonPatch.getValue()).contains(VALUE);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(JsonPatch.JsonFields.OPERATION, OPERATION.toString())
                .set(JsonPatch.JsonFields.PATH, PATH.toString())
                .set(JsonPatch.JsonFields.VALUE, VALUE)
                .build();

        final ImmutableJsonPatch jsonPatch = ImmutableJsonPatch.fromJson(jsonObject.toString());

        assertThat(jsonPatch.getOperation()).isEqualTo(OPERATION);
        assertThat(jsonPatch.getPath()).isEqualTo(PATH);
        assertThat(jsonPatch.getValue()).contains(VALUE);
    }

    @Test
    public void toJsonReturnsExpected() {
        final ImmutableJsonPatch jsonPatch = ImmutableJsonPatch.newInstance(OPERATION, PATH, VALUE);
        final JsonObject jsonObject = jsonPatch.toJson();

        assertThat(jsonObject)
                .contains(JsonPatch.JsonFields.OPERATION, OPERATION.toString())
                .contains(JsonPatch.JsonFields.PATH, PATH.toString())
                .contains(JsonPatch.JsonFields.VALUE, VALUE);
    }

    @Test
    public void newInstanceWithNullValueReturnsExpected() {
        final ImmutableJsonPatch jsonPatch = ImmutableJsonPatch.newInstance(OPERATION, PATH, null);

        assertThat(jsonPatch.getOperation()).isEqualTo(OPERATION);
        assertThat(jsonPatch.getPath()).isEqualTo(PATH);
        assertThat(jsonPatch.getValue()).isEmpty();
    }

    @Test
    public void tryToParseJsonStringContainingUnknownOperationName() {
        final String unknownOperationName = "swap";

        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(JsonPatch.JsonFields.OPERATION, unknownOperationName)
                .set(JsonPatch.JsonFields.PATH, PATH.toString())
                .set(JsonPatch.JsonFields.VALUE, VALUE)
                .build();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImmutableJsonPatch.fromJson(jsonObject.toString()))
                .withMessage("Operation <%s> is unknown!", unknownOperationName)
                .withNoCause();
    }

    @Test
    public void fromJsonWithoutValueReturnsExpected() {
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(JsonPatch.JsonFields.OPERATION, OPERATION.toString())
                .set(JsonPatch.JsonFields.PATH, PATH.toString())
                .build();

        final ImmutableJsonPatch jsonPatch = ImmutableJsonPatch.fromJson(jsonObject.toString());

        assertThat(jsonPatch.getOperation()).isEqualTo(OPERATION);
        assertThat(jsonPatch.getPath()).isEqualTo(PATH);
        assertThat(jsonPatch.getValue()).isEmpty();
    }

    @Test
    public void fromJsonWithNullLiteralReturnsExpected() {
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(JsonPatch.JsonFields.OPERATION, OPERATION.toString())
                .set(JsonPatch.JsonFields.PATH, PATH.toString())
                .set(JsonPatch.JsonFields.VALUE, JsonFactory.nullLiteral())
                .build();

        final ImmutableJsonPatch jsonPatch = ImmutableJsonPatch.fromJson(jsonObject.toString());

        assertThat(jsonPatch.getOperation()).isEqualTo(OPERATION);
        assertThat(jsonPatch.getPath()).isEqualTo(PATH);
        assertThat(jsonPatch.getValue()).contains(JsonFactory.nullLiteral());
    }

    @Test
    public void toJsonWithoutValueReturnsExpected() {
        final ImmutableJsonPatch underTest = ImmutableJsonPatch.newInstance(OPERATION, PATH, null);

        final JsonObject jsonObject = underTest.toJson();

        assertThat(jsonObject)
                .contains(JsonPatch.JsonFields.OPERATION, OPERATION.toString())
                .contains(JsonPatch.JsonFields.PATH, PATH.toString())
                .doesNotContain(JsonPatch.JsonFields.VALUE);
    }

    @Test
    public void toJsonWithNullLiteralReturnsExpected() {
        final ImmutableJsonPatch underTest = ImmutableJsonPatch.newInstance(OPERATION, PATH, JsonFactory.nullLiteral());

        final JsonObject jsonObject = underTest.toJson();

        assertThat(jsonObject)
                .contains(JsonPatch.JsonFields.OPERATION, OPERATION.toString())
                .contains(JsonPatch.JsonFields.PATH, PATH.toString())
                .contains(JsonPatch.JsonFields.VALUE, JsonFactory.nullLiteral());
    }

    @Test
    public void toStringReturnsExpected() {
        final String expected = "{\"op\":\"" + OPERATION + "\",\"path\":\"" + PATH + "\",\"value\":" + VALUE + "}";
        final ImmutableJsonPatch underTest = ImmutableJsonPatch.newInstance(OPERATION, PATH, VALUE);

        final String actual = underTest.toString();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toStringWithNullLiteralReturnsExpected() {
        final String expected =
                "{\"op\":\"" + OPERATION + "\",\"path\":\"" + PATH + "\",\"value\":" + JsonFactory.nullLiteral() + "}";
        final ImmutableJsonPatch underTest = ImmutableJsonPatch.newInstance(OPERATION, PATH, JsonFactory.nullLiteral());

        final String actual = underTest.toString();

        assertThat(actual).isEqualTo(expected);
    }

}
