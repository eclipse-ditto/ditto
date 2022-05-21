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
package org.eclipse.ditto.thingsearch.service.persistence.write.mapping;

import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonNumber;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.thingsearch.service.persistence.write.IndexLengthRestrictionEnforcer;

/**
 * Enforces limits on keys and values of a JsonObject defined in {@code IndexLengthRestrictionEnforcer}.
 */
final class IndexLengthRestrictionEnforcerVisitor implements JsonObjectVisitor<JsonValue> {

    private final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer;
    private final int maxArraySize;

    private IndexLengthRestrictionEnforcerVisitor(final String thingId, final int maxArraySize) {
        indexLengthRestrictionEnforcer = IndexLengthRestrictionEnforcer.newInstance(thingId);
        this.maxArraySize = maxArraySize;
    }

    static JsonObject enforce(final JsonObject thingJson, final int maxArraySize) {
        final String thingId = thingJson.getValueOrThrow(Thing.JsonFields.ID);
        return new IndexLengthRestrictionEnforcerVisitor(thingId, maxArraySize).eval(thingJson)
                .map(JsonValue::asObject)
                .orElseThrow();
    }

    @Override
    public Optional<JsonValue> nullValue(final JsonPointer key) {
        return singleton(key, JsonValue.nullLiteral());
    }

    @Override
    public Optional<JsonValue> bool(final JsonPointer key, final boolean value) {
        return singleton(key, JsonValue.of(value));
    }

    @Override
    public Optional<JsonValue> string(final JsonPointer key, final String value) {
        return singleton(key, JsonValue.of(value));
    }

    @Override
    public Optional<JsonValue> number(final JsonPointer key, final JsonNumber value) {
        return singleton(key, value);
    }

    @Override
    public Optional<JsonValue> array(final JsonPointer key, final Stream<JsonValue> array) {
        return Optional.of(Stream.of(array)
                .flatMap(s -> maxArraySize < 0 ? s : s.limit(maxArraySize))
                .collect(JsonCollectors.valuesToArray()));
    }

    @Override
    public Optional<JsonValue> object(final JsonPointer key, final Stream<JsonField> jsonObject) {
        return Optional.of(jsonObject.collect(JsonCollectors.fieldsToObject()));
    }

    private Optional<JsonValue> singleton(final JsonPointer key, final JsonValue jsonValue) {
        return indexLengthRestrictionEnforcer.enforce(key, jsonValue);
    }
}
