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
    public void filterJsonByPathsAncestorGrantIncludesAllDescendants() {
        final JsonObject thingJson = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("foo", "bar")
                        .set("bar", "baz")
                        .build())
                .build();

        final Set<JsonPointer> accessiblePaths = new LinkedHashSet<>();
        accessiblePaths.add(JsonPointer.of("/attributes"));

        final JsonObject filtered = JsonPartialAccessFilter.filterJsonByPaths(thingJson, accessiblePaths);

        assertThat(filtered.getValue("attributes")).isPresent();
        assertThat(filtered.getValue("attributes").get().asObject().getValue("foo")).isPresent();
        assertThat(filtered.getValue("attributes").get().asObject().getValue("bar")).isPresent();
    }

    @Test
    public void filterJsonByPathsLeafOnlyGrantExcludesSiblings() {
        final JsonObject thingJson = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("foo", "bar")
                        .set("bar", "baz")
                        .build())
                .build();

        final Set<JsonPointer> accessiblePaths = new LinkedHashSet<>();
        accessiblePaths.add(JsonPointer.of("/attributes/foo"));

        final JsonObject filtered = JsonPartialAccessFilter.filterJsonByPaths(thingJson, accessiblePaths);

        assertThat(filtered.getValue("attributes")).isPresent();
        assertThat(filtered.getValue("attributes").get().asObject().getValue("foo")).isPresent();
        assertThat(filtered.getValue("attributes").get().asObject().getValue("bar")).isEmpty();
    }

    @Test
    public void filterJsonByPathsAncestorGrantIncludesArrayValueAsIs() {
        final JsonObject thingJson = JsonFactory.newObjectBuilder()
                .set("features", JsonFactory.newObjectBuilder()
                        .set("featureA", JsonFactory.newObjectBuilder()
                                .set("items", JsonFactory.newArrayBuilder()
                                        .add("a").add("b").add("c")
                                        .build())
                                .build())
                        .build())
                .build();

        final Set<JsonPointer> accessiblePaths = new LinkedHashSet<>();
        accessiblePaths.add(JsonPointer.of("/features/featureA"));

        final JsonObject filtered = JsonPartialAccessFilter.filterJsonByPaths(thingJson, accessiblePaths);

        assertThat(filtered.getValue(JsonPointer.of("/features/featureA/items")))
                .hasValueSatisfying(value -> {
                    assertThat(value.isArray()).isTrue();
                    assertThat(value.asArray()).hasSize(3);
                });
    }

    @Test
    public void filterJsonByPathsAncestorGrantIncludesScalarDescendantAsIs() {
        final JsonObject thingJson = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("count", 42)
                        .set("active", true)
                        .build())
                .build();

        final Set<JsonPointer> accessiblePaths = new LinkedHashSet<>();
        accessiblePaths.add(JsonPointer.of("/attributes"));

        final JsonObject filtered = JsonPartialAccessFilter.filterJsonByPaths(thingJson, accessiblePaths);

        assertThat(filtered.getValue(JsonPointer.of("/attributes/count")))
                .hasValue(JsonFactory.newValue(42));
        assertThat(filtered.getValue(JsonPointer.of("/attributes/active")))
                .hasValue(JsonFactory.newValue(true));
    }

    @Test
    public void filterJsonByPathsAncestorGrantIncludesNullValueAsIs() {
        final JsonObject thingJson = JsonFactory.newObjectBuilder()
                .set("attributes", JsonFactory.newObjectBuilder()
                        .set("optional", JsonFactory.nullLiteral())
                        .build())
                .build();

        final Set<JsonPointer> accessiblePaths = new LinkedHashSet<>();
        accessiblePaths.add(JsonPointer.of("/attributes"));

        final JsonObject filtered = JsonPartialAccessFilter.filterJsonByPaths(thingJson, accessiblePaths);

        assertThat(filtered.getValue(JsonPointer.of("/attributes/optional")))
                .hasValueSatisfying(value -> assertThat(value.isNull()).isTrue());
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

    @Test
    public void parseIndexedPartialAccessPaths() {
        // Test indexed format: { "subjects": ["subj1", "subj2"], "paths": { "/path1": [0, 1], "/path2": [1] } }
        final JsonObject indexedHeader = JsonFactory.newObjectBuilder()
                .set("subjects", JsonFactory.newArrayBuilder()
                        .add("nginx:partial-access")
                        .add("nginx:another-subject")
                        .build())
                .set("paths", JsonFactory.newObjectBuilder()
                        .set("/attributes/public", JsonFactory.newArrayBuilder()
                                .add(0) // nginx:partial-access
                                .build())
                        .set("/attributes/nested/data", JsonFactory.newArrayBuilder()
                                .add(0) // nginx:partial-access
                                .build())
                        .set("/features/sensor/properties/temperature", JsonFactory.newArrayBuilder()
                                .add(0) // nginx:partial-access
                                .add(1) // nginx:another-subject
                                .build())
                        .build())
                .build();

        final var result = JsonPartialAccessFilter.parsePartialAccessPaths(indexedHeader);

        assertThat(result).isNotEmpty();
        assertThat(result).containsKey("nginx:partial-access");
        assertThat(result.get("nginx:partial-access")).containsExactlyInAnyOrder(
                JsonPointer.of("/attributes/public"),
                JsonPointer.of("/attributes/nested/data"),
                JsonPointer.of("/features/sensor/properties/temperature")
        );
        assertThat(result).containsKey("nginx:another-subject");
        assertThat(result.get("nginx:another-subject")).containsExactly(
                JsonPointer.of("/features/sensor/properties/temperature")
        );
    }

    @Test
    public void parsePartialAccessPathsReturnsEmptyForInvalidFormat() {
        final JsonObject invalidHeader = JsonFactory.newObjectBuilder()
                .set("nginx:partial-access", JsonFactory.newArrayBuilder()
                        .add("/attributes/public")
                        .build())
                .build();

        final var result = JsonPartialAccessFilter.parsePartialAccessPaths(invalidHeader);

        assertThat(result).isEmpty();
    }
}

