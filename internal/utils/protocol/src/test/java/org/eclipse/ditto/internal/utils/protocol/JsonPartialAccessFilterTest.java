/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;

/**
 * Unit test for {@link JsonPartialAccessFilter} and the underlying PathTrie logic.
 */
public final class JsonPartialAccessFilterTest {

    @Test
    public void filterJsonByPathsIncludesParentWhenChildIsAccessible() {
        final JsonObject thingJson = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("foo", "bar")
                        .build())
                .set("features", JsonFactory.newObjectBuilder()
                        .set("fluxCompensator", JsonFactory.newObjectBuilder()
                                .set("properties", JsonFactory.newObjectBuilder()
                                        .set("baz", 42)
                                        .set("qux", 100)
                                        .build())
                                .build())
                        .build())
                .build();

        final Set<JsonPointer> accessiblePaths = new LinkedHashSet<>();
        accessiblePaths.add(JsonPointer.of("/attributes"));
        accessiblePaths.add(JsonPointer.of("/attributes/foo"));
        accessiblePaths.add(JsonPointer.of("/features/fluxCompensator/properties/baz"));

        final JsonObject filtered = JsonPartialAccessFilter.filterJsonByPaths(thingJson, accessiblePaths);

        System.err.println("DEBUG JsonPartialAccessFilterTest: filtered=" + filtered);
        assertThat(filtered.getValue("attributes")).isPresent();
        assertThat(filtered.getValue("attributes").get().asObject().getValue("foo")).isPresent();
        assertThat(filtered.getValue("features")).isPresent();
        assertThat(filtered.getValue("features").get().asObject().getValue("fluxCompensator")).isPresent();
        assertThat(filtered.getValue("features").get().asObject()
                .getValue("fluxCompensator").get().asObject()
                .getValue("properties").get().asObject()
                .getValue("baz")).isPresent();
        assertThat(filtered.getValue("features").get().asObject()
                .getValue("fluxCompensator").get().asObject()
                .getValue("properties").get().asObject()
                .getValue("qux")).isEmpty();
    }

    @Test
    public void filterJsonByPathsWithExactPaths() {
        final JsonObject thingJson = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("foo", "bar")
                        .set("bar", "baz")
                        .build())
                .build();

        final Set<JsonPointer> accessiblePaths = new LinkedHashSet<>();
        accessiblePaths.add(JsonPointer.of("/attributes"));
        accessiblePaths.add(JsonPointer.of("/attributes/foo"));

        final JsonObject filtered = JsonPartialAccessFilter.filterJsonByPaths(thingJson, accessiblePaths);

        assertThat(filtered.getValue("attributes")).isPresent();
        assertThat(filtered.getValue("attributes").get().asObject().getValue("foo")).isPresent();
        assertThat(filtered.getValue("attributes").get().asObject().getValue("bar")).isEmpty();
    }

    @Test
    public void filterJsonByPathsWithRootPathReturnsOriginal() {
        final JsonObject thingJson = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("foo", "bar")
                        .build())
                .build();

        final Set<JsonPointer> accessiblePaths = new LinkedHashSet<>();
        accessiblePaths.add(JsonPointer.empty());

        final JsonObject filtered = JsonPartialAccessFilter.filterJsonByPaths(thingJson, accessiblePaths);

        assertThat(filtered).isEqualTo(thingJson);
    }

    @Test
    public void filterJsonByPathsWithEmptyAccessiblePathsReturnsEmpty() {
        final JsonObject thingJson = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("foo", "bar")
                        .build())
                .build();

        final Set<JsonPointer> accessiblePaths = new LinkedHashSet<>();

        final JsonObject filtered = JsonPartialAccessFilter.filterJsonByPaths(thingJson, accessiblePaths);

        assertThat(filtered).isEmpty();
    }
}

