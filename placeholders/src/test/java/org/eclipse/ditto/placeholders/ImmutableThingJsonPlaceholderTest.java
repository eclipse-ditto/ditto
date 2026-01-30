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
package org.eclipse.ditto.placeholders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Tests {@link ImmutableThingJsonPlaceholder}.
 */
public class ImmutableThingJsonPlaceholderTest {

    private static final Placeholder<JsonObject> UNDER_TEST = PlaceholderFactory.newThingJsonPlaceholder();

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set("attributes", JsonObject.newBuilder()
                    .set("int", 1)
                    .set("num", 2.3)
                    .set("bool", true)
                    .set("obj", JsonObject.newBuilder()
                            .set("str", "bar")
                            .set("arr", JsonArray.newBuilder()
                                    .add(1, 2, 3)
                                    .build())
                            .set("evenDeeper", JsonObject.newBuilder()
                                    .set("str", "bar2")
                                    .build())
                            .build())
                    .build())
            .set("features", JsonObject.newBuilder()
                    .set("some-feature", JsonObject.newBuilder()
                            .set("properties", JsonObject.newBuilder()
                                    .set("some-prop", "abc")
                                    .build())
                            .build())
                    .build())
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableThingJsonPlaceholder.class)
                .suppress(Warning.INHERITED_DIRECTLY_FROM_OBJECT)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testReplaceIntegerAttribute() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_JSON, "attributes/int")).contains(String.valueOf(1));
    }

    @Test
    public void testReplaceNumberAttribute() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_JSON, "attributes/num")).contains(String.valueOf(2.3));
    }

    @Test
    public void testReplaceBooleanAttribute() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_JSON, "attributes/bool")).contains(String.valueOf(true));
    }

    @Test
    public void testReplaceNestedStrAttribute() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_JSON, "attributes/obj/str")).contains("bar");
    }

    @Test
    public void testReplaceNestedArrAttribute() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_JSON, "attributes/obj/arr"))
                .containsExactly(String.valueOf(1), String.valueOf(2), String.valueOf(3));
    }

    @Test
    public void testReplaceObjAttribute() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_JSON, "attributes/obj/evenDeeper")).contains("{\"str\":\"bar2\"}");
    }

    @Test
    public void testReplaceDeeperNestedStrAttribute() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_JSON, "attributes/obj/evenDeeper/str")).contains("bar2");
    }

    @Test
    public void testReplaceStrFeatureProperty() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_JSON, "features/some-feature/properties/some-prop"))
                .contains("abc");
    }

    @Test
    public void testResolvesWithThingJsonPrefixInName() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_JSON, "thing-json:attributes/int")).contains(String.valueOf(1));
    }

    @Test
    public void testUnknownPlaceholderReturnsEmpty() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_JSON, "unknown")).isEmpty();
    }

    @Test
    public void testResolvingWithNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> UNDER_TEST.resolveValues(KNOWN_JSON, null));
    }

    @Test
    public void testResolvingWithEmptyString() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> UNDER_TEST.resolveValues(KNOWN_JSON, ""));
    }

}
