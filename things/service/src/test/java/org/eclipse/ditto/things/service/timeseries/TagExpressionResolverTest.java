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
package org.eclipse.ditto.things.service.timeseries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;
import org.junit.Test;

public final class TagExpressionResolverTest {

    private static final JsonObject THING_JSON = JsonObject.newBuilder()
            .set("attributes", JsonObject.newBuilder()
                    .set("floor", 2)
                    .set("floorText", "2")
                    .set("heated", true)
                    .set("building", "a")
                    .set("rooms", JsonArray.of(JsonValue.of(4), JsonValue.of(5)))
                    .set("labels", JsonArray.of(JsonValue.of("x"), JsonValue.of("y")))
                    .set("address", JsonObject.newBuilder().set("city", "Berlin").build())
                    .set("removed", JsonValue.nullLiteral())
                    .set("sparse", JsonArray.of(JsonValue.nullLiteral(), JsonValue.of(1)))
                    .build())
            .build();

    private final TagExpressionResolver underTest = new TagExpressionResolver(THING_JSON);

    @Test
    public void singlePlaceholderKeepsTheJsonType() {
        assertThat(underTest.resolve("{{ thing-json:attributes/floor }}")).contains(JsonValue.of(2));
        assertThat(underTest.resolve("{{thing-json:attributes/heated}}")).contains(JsonValue.of(true));
        assertThat(underTest.resolve("{{ thing-json:attributes/floorText }}")).contains(JsonValue.of("2"));
    }

    @Test
    public void arrayFieldResolvesToItsFirstElementLikeThePlaceholder() {
        assertThat(underTest.resolve("{{ thing-json:attributes/rooms }}")).contains(JsonValue.of(4));
        assertThat(underTest.resolve("{{ thing-json:attributes/labels }}")).contains(JsonValue.of("x"));
        assertThat(underTest.resolve("{{ thing-json:attributes/labels }}-x")).contains(JsonValue.of("x-x"));
    }

    @Test
    public void expressionWithMoreThanThePlaceholderIsAString() {
        assertThat(underTest.resolve("floor-{{ thing-json:attributes/floor }}")).contains(JsonValue.of("floor-2"));
        assertThat(underTest.resolve("{{ thing-json:attributes/building | fn:upper() }}"))
                .contains(JsonValue.of("A"));
    }

    @Test
    public void objectFieldIsStoredAsJsonString() {
        assertThat(underTest.resolve("{{ thing-json:attributes/address }}"))
                .contains(JsonValue.of("{\"city\":\"Berlin\"}"));
    }

    @Test
    public void missingOrNullFieldResolvesToNothing() {
        assertThat(underTest.resolve("{{ thing-json:attributes/unknown }}")).isEmpty();
        assertThat(underTest.resolve("{{ thing-json:attributes/removed }}")).isEmpty();
    }

    @Test
    public void nullArrayElementIsAStringLikeInAnyOtherExpression() {
        assertThat(underTest.resolve("{{ thing-json:attributes/sparse }}")).contains(JsonValue.of("null"));
        assertThat(underTest.resolve("{{ thing-json:attributes/sparse }}-x")).contains(JsonValue.of("null-x"));
    }

    @Test
    public void unknownPlaceholderFails() {
        assertThatExceptionOfType(UnresolvedPlaceholderException.class)
                .isThrownBy(() -> underTest.resolve("{{ thing:id }}"));
    }

}
