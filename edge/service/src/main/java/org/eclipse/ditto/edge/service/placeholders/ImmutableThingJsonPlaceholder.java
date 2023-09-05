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

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;

/**
 * Placeholder implementation that replaces {@code thing-json:attributes/some-attr} and other arbitrary json values inside
 * a Thing.
 */
@Immutable
final class ImmutableThingJsonPlaceholder implements ThingJsonPlaceholder {

    /**
     * Singleton instance of the ImmutableThingJsonPlaceholder.
     */
    static final ImmutableThingJsonPlaceholder INSTANCE = new ImmutableThingJsonPlaceholder();

    @Override
    public String getPrefix() {
        return "thing-json";
    }

    @Override
    public List<String> getSupportedNames() {
        // supports any names (interpreted as JsonPointer)
        return List.of();
    }

    @Override
    public boolean supports(final String name) {
        // supports any names (interpreted as JsonPointer) BUT the ones supported by ImmutableThingPlaceholder
        return !List.of("namespace", "name", "id").contains(name);
    }

    @Override
    public List<String> resolveValues(final Thing thing, final String placeholder) {
        checkNotNull(thing, "thing");
        argumentNotEmpty(placeholder, "placeholder");
        final Optional<JsonValue> value = thing.toJson(FieldType.all()).getValue(placeholder);
        return value.filter(JsonValue::isArray)
                .map(JsonValue::asArray)
                .map(array -> array.stream()
                        .map(JsonValue::formatAsString)
                        .toList())
                .or(() -> value.map(JsonValue::formatAsString)
                        .map(Collections::singletonList))
                .orElseGet(Collections::emptyList);
    }
}
