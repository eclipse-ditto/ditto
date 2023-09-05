/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.edge.service.placeholders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Thing;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Tests {@link ImmutableThingJsonPlaceholder}.
 */
public class ImmutableThingJsonPlaceholderTest {

    private static final Thing KNOWN_THING = Thing.newBuilder()
            .setAttributes(
                    JsonObject.newBuilder()
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
                            .build()
            )
            .setFeature("some-feature", FeatureProperties.newBuilder()
                    .set("some-prop", "abc")
                    .build()
            )
            .build();
    private static final ThingJsonPlaceholder UNDER_TEST = ImmutableThingJsonPlaceholder.INSTANCE;

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(ImmutableThingJsonPlaceholder.class, MutabilityMatchers.areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableThingJsonPlaceholder.class)
                .suppress(Warning.INHERITED_DIRECTLY_FROM_OBJECT)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testReplaceIntegerAttribute() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_THING, "attributes/int")).contains(String.valueOf(1));
    }

    @Test
    public void testReplaceNumberAttribute() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_THING, "attributes/num")).contains(String.valueOf(2.3));
    }

    @Test
    public void testReplaceBooleanAttribute() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_THING, "attributes/bool")).contains(String.valueOf(true));
    }

    @Test
    public void testReplaceNestedStrAttribute() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_THING, "attributes/obj/str")).contains("bar");
    }

    @Test
    public void testReplaceNestedArrAttribute() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_THING, "attributes/obj/arr"))
                .containsExactly(String.valueOf(1), String.valueOf(2), String.valueOf(3));
    }

    @Test
    public void testReplaceObjAttribute() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_THING, "attributes/obj/evenDeeper")).contains("{\"str\":\"bar2\"}");
    }

    @Test
    public void testReplaceDeeperNestedStrAttribute() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_THING, "attributes/obj/evenDeeper/str")).contains("bar2");
    }

    @Test
    public void testReplaceStrFeatureProperty() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_THING, "features/some-feature/properties/some-prop"))
                .contains("abc");
    }

    @Test
    public void testUnknownPlaceholderReturnsEmpty() {
        assertThat(UNDER_TEST.resolveValues(KNOWN_THING, "unknown")).isEmpty();
    }

    @Test
    public void testResolvingWithNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> UNDER_TEST.resolveValues(KNOWN_THING, null));
    }

    @Test
    public void testResolvingWithEmptyString() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> UNDER_TEST.resolveValues(KNOWN_THING, ""));
    }

}
