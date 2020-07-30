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
package org.eclipse.ditto.model.things;

import static org.eclipse.ditto.model.things.assertions.DittoThingsAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.io.IOException;

import org.eclipse.ditto.json.BinaryToHexConverter;
import org.eclipse.ditto.json.CborFactory;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKeyInvalidException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.entity.id.restriction.LengthRestrictionTestBase;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableAttributes}.
 */
public final class ImmutableAttributesTest extends LengthRestrictionTestBase {

    private static final int KNOWN_INT_42 = 42;

    private static final JsonObject KNOWN_JSON_OBJECT = JsonFactory.newObjectBuilder()
            .set("someStringAttribute", "someValue")
            .set("someIntAttribute", KNOWN_INT_42)
            .set("someBoolAttribute", true)
            .set("someArrayAttribute", JsonFactory.newArray())
            .set("someObjectAttribute", JsonFactory.newObject())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableAttributes.class,
                areImmutable(),
                provided(JsonObject.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableAttributes.class)
                .usingGetClass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceFromNullJsonObject() {
        ImmutableAttributes.of(null);
    }


    @Test
    public void emptyAttributesIsEmpty() {
        final Attributes emptyAttributes = ImmutableAttributes.empty();

        assertThat(emptyAttributes).isEmpty();
    }


    @Test
    public void createImmutableAttributesOfImmutableAttributesReturnsNoNewObject() {
        final Attributes underTest = ImmutableAttributes.of(TestConstants.Thing.ATTRIBUTES);

        assertThat(underTest).isSameAs(TestConstants.Thing.ATTRIBUTES);
    }


    @Test
    public void toJsonReturnsSameAttributesObject() {
        final Attributes underTest = ImmutableAttributes.of(TestConstants.Thing.ATTRIBUTES);

        assertThat(underTest.toJson()).isSameAs(underTest);
    }


    @Test
    public void createInstanceFromJsonObject() {
        final Attributes underTest = ImmutableAttributes.of(KNOWN_JSON_OBJECT);

        assertThat(underTest.toJson()).isEqualTo(KNOWN_JSON_OBJECT);
    }


    @Test
    public void ensureAttributesJsonCorrectness() {
        final Attributes attributes = ImmutableAttributes.empty()
                .setValue("someIntAttribute", KNOWN_INT_42)
                .setValue("someStringAttribute", "someValue")
                .setValue("someBoolAttribute", true)
                .setValue("someArrayAttribute", JsonFactory.newArray())
                .setValue("someObjectAttribute", JsonFactory.newObject());

        assertThat(attributes.toJson()).isEqualTo(KNOWN_JSON_OBJECT);
    }


    @Test
    public void ensureAttributesNewBuilderWorks() {
        final Attributes attributes = ImmutableAttributes.empty()
                .setValue("someIntAttribute", KNOWN_INT_42)
                .setValue("someStringAttribute", "someValue")
                .setValue("someBoolAttribute", true)
                .setValue("someArrayAttribute", JsonFactory.newArray())
                .setValue("someObjectAttribute", JsonFactory.newObject());

        final AttributesBuilder attributesBuilder = Attributes.newBuilder()
                .set("someIntAttribute", KNOWN_INT_42)
                .set("someStringAttribute", "someValue")
                .set("someBoolAttribute", true)
                .set("someArrayAttribute", JsonFactory.newArray())
                .set("someObjectAttribute", JsonFactory.newObject());

        assertThat(attributes).isEqualTo(attributesBuilder.build());
    }


    @Test
    public void ensureAttributesToBuilderWorks() {
        final Attributes attributes = ImmutableAttributes.empty()
                .setValue("someIntAttribute", KNOWN_INT_42)
                .setValue("someStringAttribute", "someValue")
                .setValue("someBoolAttribute", true)
                .setValue("someArrayAttribute", JsonFactory.newArray())
                .setValue("someObjectAttribute", JsonFactory.newObject());

        assertThat(attributes).isEqualTo(attributes.toBuilder().build());
    }

    @Test
    public void writeValueWritesExpected() throws IOException {
        assertThat(BinaryToHexConverter.toHexString(CborFactory.toByteBuffer(ImmutableAttributes.of(KNOWN_JSON_OBJECT))))
                .isEqualTo(BinaryToHexConverter.toHexString(CborFactory.toByteBuffer(KNOWN_JSON_OBJECT)));
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createInvalidAttributeKey() {
        final String invalidAttributeKey = "invalid/";
        TestConstants.Thing.ATTRIBUTES.setValue(invalidAttributeKey, "invalidAttributeKey")
                .toBuilder()
                .build();
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createInvalidNestedAttributeKey() {
        final String validAttributeKey = "valid";
        final JsonObject invalidJsonObject = JsonObject.newBuilder()
                .set("foo/", "bar")
                .build();
        TestConstants.Thing.ATTRIBUTES.setValue(validAttributeKey, invalidJsonObject)
                .toBuilder()
                .build();
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createInvalidNestedNestedAttributeKey() {
        final String validAttributeKey = "valid";
        final JsonObject invalidJsonObject = JsonObject.newBuilder()
                .set("foo/", "bar")
                .build();
        final JsonObject validJsonObject = JsonObject.newBuilder()
                .set("foo", "bar")
                .set("invalid", invalidJsonObject)
                .build();
        TestConstants.Thing.ATTRIBUTES.setValue(validAttributeKey, validJsonObject)
                .toBuilder()
                .build();
    }

    @Test(expected = JsonKeyInvalidException.class)
    public void createTooLargePropertyKey() {
        final String tooLargePropertyKey = generateStringExceedingMaxLength();
        TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES.setValue(tooLargePropertyKey, "tooLargePropertyKey")
                .toBuilder()
                .build();
    }
}
