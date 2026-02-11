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

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Placeholder implementation that resolves {@code thing-json:&lt;json-pointer&gt;} from a {@link JsonObject}
 * (e.g. thing JSON). Supports any path in JsonPointer notation except {@code namespace}, {@code name}, {@code id}.
 */
@Immutable
final class ImmutableThingJsonPlaceholder implements Placeholder<JsonObject> {

    private static final String PREFIX = "thing-json";
    private static final String PREFIX_COLON = PREFIX + ":";

    /**
     * Singleton instance.
     */
    static final ImmutableThingJsonPlaceholder INSTANCE = new ImmutableThingJsonPlaceholder();

    private ImmutableThingJsonPlaceholder() {
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public List<String> getSupportedNames() {
        return Collections.emptyList();
    }

    @Override
    public boolean supports(final String name) {
        return !Arrays.asList("namespace", "name", "id").contains(name);
    }

    @Override
    public List<String> resolveValues(final JsonObject thingJson, final String name) {
        checkNotNull(thingJson, "thingJson");
        argumentNotEmpty(name, "name");
        final String trimmed = name.trim();
        final String path = trimmed.startsWith(PREFIX_COLON)
                ? trimmed.substring(PREFIX_COLON.length()).trim()
                : trimmed;
        if (path.isEmpty()) {
            return Collections.emptyList();
        }
        final Optional<JsonValue> value = thingJson.getValue(JsonFactory.newPointer(path));
        if (value.isPresent()) {
            final JsonValue v = value.get();
            if (v.isArray()) {
                return v.asArray().stream()
                        .map(JsonValue::formatAsString)
                        .collect(Collectors.toList());
            } else {
                return Collections.singletonList(v.formatAsString());
            }
        }
        return Collections.emptyList();
    }
}
