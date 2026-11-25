/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

/**
 * Tests that JsonObjects holding {@link Attributes} or {@link FeatureProperties} have the same hash code as equal
 * JsonObjects holding the plain JSON instead, as required by the {@code equals}/{@code hashCode} contract.
 */
public final class JsonObjectWrapperHashCodeTest {

    private static final JsonObject JSON = JsonObject.newBuilder()
            .set("temperature", 21.5)
            .set("modes", JsonArray.of(JsonValue.of("eco"), JsonValue.of("comfort")))
            .set("nested", JsonObject.newBuilder().set("on", true).build())
            .build();

    @Test
    public void attributesHaveTheHashCodeOfTheWrappedJson() {
        final Attributes attributes = ThingsModelFactory.newAttributes(JSON);

        assertThat(attributes).hasSameHashCodeAs(JSON);
        assertEqualWithSameHashCodeWhenHeld(attributes, JSON);
    }

    @Test
    public void emptyAttributesHaveTheHashCodeOfAnEmptyObject() {
        final Attributes attributes = ThingsModelFactory.emptyAttributes();

        assertThat(attributes).hasSameHashCodeAs(JsonObject.empty());
        assertEqualWithSameHashCodeWhenHeld(attributes, JsonObject.empty());
    }

    @Test
    public void nullAttributesHaveTheHashCodeOfJsonNull() {
        final Attributes nullAttributes = ThingsModelFactory.nullAttributes();

        assertThat(nullAttributes).hasSameHashCodeAs(JsonFactory.nullLiteral());
        assertEqualWithSameHashCodeWhenHeld(nullAttributes, JsonFactory.nullLiteral());
        assertEqualWithSameHashCodeWhenHeld(nullAttributes, JsonFactory.nullObject());
    }

    @Test
    public void featurePropertiesHaveTheHashCodeOfTheWrappedJson() {
        final FeatureProperties featureProperties = ThingsModelFactory.newFeatureProperties(JSON);

        assertThat(featureProperties).hasSameHashCodeAs(JSON);
        assertEqualWithSameHashCodeWhenHeld(featureProperties, JSON);
    }

    @Test
    public void emptyFeaturePropertiesHaveTheHashCodeOfAnEmptyObject() {
        final FeatureProperties featureProperties = ThingsModelFactory.emptyFeatureProperties();

        assertThat(featureProperties).hasSameHashCodeAs(JsonObject.empty());
        assertEqualWithSameHashCodeWhenHeld(featureProperties, JsonObject.empty());
    }

    @Test
    public void nullFeaturePropertiesHaveTheHashCodeOfJsonNull() {
        final FeatureProperties nullFeatureProperties = ThingsModelFactory.nullFeatureProperties();

        assertThat(nullFeatureProperties).hasSameHashCodeAs(JsonFactory.nullLiteral());
        assertEqualWithSameHashCodeWhenHeld(nullFeatureProperties, JsonFactory.nullLiteral());
        assertEqualWithSameHashCodeWhenHeld(nullFeatureProperties, JsonFactory.nullObject());
    }

    @Test
    public void attributesAndFeaturePropertiesWithTheSameJsonHaveTheSameHashCode() {
        assertEqualWithSameHashCodeWhenHeld(ThingsModelFactory.newAttributes(JSON),
                ThingsModelFactory.newFeatureProperties(JSON));
        assertEqualWithSameHashCodeWhenHeld(ThingsModelFactory.nullAttributes(),
                ThingsModelFactory.nullFeatureProperties());
    }

    @Test
    public void wrapperOfOtherJsonIsNotEqualWhenHeld() {
        final FeatureProperties otherProperties =
                ThingsModelFactory.newFeatureProperties(JSON.setValue("temperature", 22));

        assertThat(object("properties", otherProperties)).isNotEqualTo(object("properties", JSON));
        assertThat(object("properties", ThingsModelFactory.nullFeatureProperties()))
                .isNotEqualTo(object("properties", JsonObject.empty()));
    }

    /**
     * Asserts that JsonObjects holding {@code wrapper} or {@code json} directly, nested in another object and inside
     * an array are mutually equal and have the same hash code.
     */
    private static void assertEqualWithSameHashCodeWhenHeld(final JsonValue wrapper, final JsonValue json) {
        assertMutuallyEqualWithSameHashCode(object("value", wrapper), object("value", json));
        assertMutuallyEqualWithSameHashCode(object("outer", object("value", wrapper)),
                object("outer", object("value", json)));
        assertMutuallyEqualWithSameHashCode(object("list", JsonArray.of(object("value", wrapper))),
                object("list", JsonArray.of(object("value", json))));
    }

    private static void assertMutuallyEqualWithSameHashCode(final JsonObject one, final JsonObject other) {
        assertThat(one).isEqualTo(other);
        assertThat(other).isEqualTo(one);
        assertThat(one).hasSameHashCodeAs(other);
    }

    private static JsonObject object(final String key, final JsonValue value) {
        return JsonObject.newBuilder().set(key, value).build();
    }

}
