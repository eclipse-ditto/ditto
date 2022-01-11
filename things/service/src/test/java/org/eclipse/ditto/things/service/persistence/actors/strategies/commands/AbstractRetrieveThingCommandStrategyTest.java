/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.junit.Test;

public class AbstractRetrieveThingCommandStrategyTest {

    private static final Features FEATURES = Features.newBuilder()
            .set(Feature.newBuilder().withId("f1").build())
            .set(Feature.newBuilder().withId("f2").build())
            .build();

    @Test
    public void testExpandFeatureIdWildcardAtRootLevel() {

        final JsonFieldSelector fieldSelector = JsonFieldSelector.newInstance("thingId",
                "attributes", "features/*/properties/connected");
        final JsonFieldSelector expected = JsonFieldSelector.newInstance("thingId",
                "attributes", "features/f1/properties/connected", "features/f2/properties/connected");
        final JsonFieldSelector expanded =
                AbstractRetrieveThingCommandStrategy.expandFeatureIdWildcard(fieldSelector,
                        Thing.JsonFields.FEATURES.getPointer(), FEATURES);

        assertThat(expanded).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testExpandFeatureIdWildcardAtFeaturesLevel() {

        final JsonFieldSelector fieldSelector = JsonFieldSelector.newInstance("thingId",
                "attributes", "*/properties/connected");
        final JsonFieldSelector expected = JsonFieldSelector.newInstance("thingId",
                "attributes", "f1/properties/connected", "f2/properties/connected");
        final JsonFieldSelector expanded =
                AbstractRetrieveThingCommandStrategy.expandFeatureIdWildcard(fieldSelector, JsonPointer.empty(),
                        FEATURES);

        assertThat(expanded).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testReplaceLevel() {
        testReplaceLevel("a/*/c/d", 1, "b", "a/b/c/d");
        testReplaceLevel("a/b/c", 0, "a", "a/b/c");
        testReplaceLevel("a/b/*", 2, "c", "a/b/c");
        testReplaceLevel("a/b/c", 3, "x", "a/b/c");
        testReplaceLevel("a/b/c/d", -1, "x", "a/b/c/d");
    }

    private void testReplaceLevel(final String original, final int level, final String replacement,
            final String expected) {

        final JsonPointer actual = AbstractRetrieveThingCommandStrategy.replaceLevel(JsonPointer.of(original), level,
                JsonPointer.of(replacement));

        assertThat((Object) actual).isEqualTo(JsonPointer.of(expected));
    }
}
