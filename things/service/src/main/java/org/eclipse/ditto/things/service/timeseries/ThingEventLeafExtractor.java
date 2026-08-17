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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * Decomposes any {@link ThingEvent} into the scalar feature-property leaves it changed. Driven
 * by {@code event.getResourcePath()} + {@code event.getEntity()}, so every create/modify/merge
 * shape is handled without enumerating event subtypes. Non-feature resources (attributes,
 * policyId, ...) and value-less events (deletes) yield no leaves.
 */
final class ThingEventLeafExtractor {

    private static final String FEATURES_FIELD = "features";
    private static final String PROPERTIES_FIELD = "properties";

    private ThingEventLeafExtractor() {
        throw new AssertionError();
    }

    static List<PropertyLeaf> extractLeaves(final ThingEvent<?> event) {
        final JsonValue value = event.getEntity().orElse(null);
        if (value == null) {
            return List.of();
        }
        final List<PropertyLeaf> leaves = new ArrayList<>();
        decompose(event.getResourcePath(), value, leaves);
        return leaves;
    }

    /**
     * Maps a resource path + value onto feature-property leaves, handling every granularity:
     * whole Thing ({@code /}), all features ({@code /features}), a single feature, a feature's
     * properties, or a sub-path within one property.
     */
    private static void decompose(final JsonPointer path, final JsonValue value,
            final List<PropertyLeaf> leaves) {

        final int levels = path.getLevelCount();
        if (levels == 0) {
            asObject(value)
                    .flatMap(thingJson -> objectAt(thingJson, FEATURES_FIELD))
                    .ifPresent(features -> mergeFeatures(features, leaves));
            return;
        }
        if (!FEATURES_FIELD.equals(path.get(0).map(Object::toString).orElse(null))) {
            return;
        }
        if (levels == 1) {
            asObject(value).ifPresent(features -> mergeFeatures(features, leaves));
            return;
        }
        final String featureId = path.get(1).map(Object::toString).orElse(null);
        if (featureId == null) {
            return;
        }
        if (levels == 2) {
            asObject(value)
                    .flatMap(featureJson -> objectAt(featureJson, PROPERTIES_FIELD))
                    .ifPresent(props -> flattenProperties(featureId, props, leaves));
            return;
        }
        if (!PROPERTIES_FIELD.equals(path.get(2).map(Object::toString).orElse(null))) {
            return;
        }
        if (levels == 3) {
            asObject(value).ifPresent(props -> flattenProperties(featureId, props, leaves));
            return;
        }
        // levels >= 4: features/<id>/properties/<rest...>
        flatten(featureId, path.getSubPointer(3).orElse(JsonPointer.empty()), value, leaves);
    }

    private static void mergeFeatures(final JsonObject features, final List<PropertyLeaf> leaves) {
        for (final JsonField feature : features) {
            asObject(feature.getValue())
                    .flatMap(featureJson -> objectAt(featureJson, PROPERTIES_FIELD))
                    .ifPresent(props -> flattenProperties(feature.getKeyName(), props, leaves));
        }
    }

    private static void flattenProperties(final String featureId, final JsonObject properties,
            final List<PropertyLeaf> leaves) {

        for (final JsonField field : properties) {
            flatten(featureId, JsonPointer.empty().addLeaf(field.getKey()), field.getValue(), leaves);
        }
    }

    /**
     * Reduces {@code value} to scalar leaves. Objects descend one key at a time; arrays are
     * skipped (a single data point is a scalar reading); scalars (incl. null) emit a leaf.
     */
    private static void flatten(final String featureId, final JsonPointer base, final JsonValue value,
            final List<PropertyLeaf> leaves) {

        if (value.isObject()) {
            for (final JsonField field : value.asObject()) {
                flatten(featureId, base.addLeaf(field.getKey()), field.getValue(), leaves);
            }
        } else if (!value.isArray()) {
            leaves.add(new PropertyLeaf(featureId, base, value));
        }
    }

    private static Optional<JsonObject> asObject(final JsonValue value) {
        return value.isObject() ? Optional.of(value.asObject()) : Optional.empty();
    }

    private static Optional<JsonObject> objectAt(final JsonObject object, final String key) {
        return object.getValue(key).filter(JsonValue::isObject).map(JsonValue::asObject);
    }

    record PropertyLeaf(String featureId, JsonPointer path, JsonValue value) {}
}
