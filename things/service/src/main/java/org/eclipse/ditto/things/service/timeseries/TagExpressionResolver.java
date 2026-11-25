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

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.Placeholder;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.PlaceholderFilter;

/**
 * Resolves a timeseries tag expression with the {@code thing-json} placeholder and keeps the JSON type of the Thing
 * field when the expression resolves to exactly that field's value, e.g. {@code {{ thing-json:attributes/floor }}}.
 * Any other result, e.g. {@code {{ thing-json:attributes/floor }}-x}, is a string. An array field resolves to its
 * first element, as for every other use of the placeholder.
 * <p>
 * Not thread-safe: create one instance per Thing snapshot and resolve its tags on one thread.
 */
final class TagExpressionResolver {

    private static final Placeholder<JsonObject> THING_JSON = PlaceholderFactory.newThingJsonPlaceholder();

    private final JsonObject thingJson;
    private final List<List<JsonValue>> lookups = new ArrayList<>();
    private final ExpressionResolver resolver;

    TagExpressionResolver(final JsonObject thingJson) {
        this.thingJson = thingJson;
        resolver = PlaceholderFactory.newExpressionResolver(new RecordingThingJsonPlaceholder(), thingJson);
    }

    /**
     * @return the resolved tag value, or empty if the expression resolves to nothing, e.g. a missing or null field.
     * @throws RuntimeException if the expression is malformed or uses an unknown placeholder.
     */
    Optional<JsonValue> resolve(final String expression) {
        lookups.clear();
        final Optional<String> resolved = PlaceholderFilter.applyOrElseDelete(expression, resolver);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        if (lookups.size() == 1 && !lookups.get(0).isEmpty()) {
            final JsonValue field = lookups.get(0).get(0);
            // JsonNull reports isObject() as true, so a null array element stays a string.
            if (field.formatAsString().equals(resolved.get()) && !field.isObject() && !field.isArray()) {
                return Optional.of(field);
            }
        }
        return Optional.of(JsonValue.of(resolved.get()));
    }

    private final class RecordingThingJsonPlaceholder implements Placeholder<JsonObject> {

        @Override
        public String getPrefix() {
            return THING_JSON.getPrefix();
        }

        @Override
        public List<String> getSupportedNames() {
            return THING_JSON.getSupportedNames();
        }

        @Override
        public boolean supports(final String name) {
            return THING_JSON.supports(name);
        }

        @Override
        public List<String> resolveValues(final JsonObject placeholderSource, final String name) {
            final List<String> values = THING_JSON.resolveValues(placeholderSource, name);
            lookups.add(typedValues(name));
            return values;
        }

        private List<JsonValue> typedValues(final String name) {
            final String path = name.trim();
            if (path.isEmpty()) {
                return List.of();
            }
            return thingJson.getValue(JsonPointer.of(path))
                    .map(value -> value.isArray() ? value.asArray().stream().toList() : List.of(value))
                    .orElse(List.of());
        }
    }

}
