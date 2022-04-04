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
package org.eclipse.ditto.json;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * This class is responsible to compute or apply a JSON merge patch according to
 * <a href="https://datatracker.ietf.org/doc/html/rfc7386">RFC 7386</a> for {@link JsonValue json values}.
 *
 * @since 2.4.0
 */
@Immutable
public final class JsonMergePatch {

    private final JsonValue mergePatch;

    private JsonMergePatch(final JsonValue mergePatch) {
        this.mergePatch = mergePatch;
    }

    /**
     * This method computes the change from the given {@code oldValue} to the given {@code newValue}.
     * The result is a JSON merge patch according to <a href="https://datatracker.ietf.org/doc/html/rfc7386">RFC 7386</a>.
     *
     * @param oldValue the original value
     * @param newValue the new changed value
     * @return a JSON merge patch according to <a href="https://datatracker.ietf.org/doc/html/rfc7386">RFC 7386</a> or empty if values are equal.
     */
    public static Optional<JsonMergePatch> compute(final JsonValue oldValue, final JsonValue newValue) {
        return computeForValue(oldValue, newValue).map(JsonMergePatch::of);
    }

    private static Optional<JsonValue> computeForValue(final JsonValue oldValue, final JsonValue newValue) {
        @Nullable final JsonValue diff;
        if (oldValue.equals(newValue)) {
            diff = null;
        } else if (oldValue.isObject() && newValue.isObject()) {
            diff = computeForObject(oldValue.asObject(), newValue.asObject()).orElse(null);
        } else {
            diff = newValue;
        }
        return Optional.ofNullable(diff);
    }

    private static Optional<JsonObject> computeForObject(final JsonObject oldJsonObject,
            final JsonObject newJsonObject) {
        final JsonObjectBuilder builder = JsonObject.newBuilder();
        final List<JsonKey> oldKeys = oldJsonObject.getKeys();
        final List<JsonKey> newKeys = newJsonObject.getKeys();

        final List<JsonKey> addedKeys = newKeys.stream()
                .filter(key -> !oldKeys.contains(key))
                .collect(Collectors.toList());
        addedKeys.forEach(key -> newJsonObject.getValue(key).ifPresent(value -> builder.set(key, value)));

        final List<JsonKey> deletedKeys = oldKeys.stream()
                .filter(key -> !newKeys.contains(key))
                .collect(Collectors.toList());
        deletedKeys.forEach(key -> builder.set(key, JsonValue.nullLiteral()));

        final List<JsonKey> keptKeys = oldKeys.stream()
                .filter(newKeys::contains)
                .collect(Collectors.toList());
        keptKeys.forEach(key -> {
            final Optional<JsonValue> oldValue = oldJsonObject.getValue(key);
            final Optional<JsonValue> newValue = newJsonObject.getValue(key);
            if (oldValue.isPresent() && newValue.isPresent()) {
                computeForValue(oldValue.get(), newValue.get()).ifPresent(diff -> builder.set(key, diff));
            } else if (oldValue.isPresent()) {
                // Should never happen because deleted keys were handled before
                builder.set(key, JsonValue.nullLiteral());
            } else if (newValue.isPresent()) {
                // Should never happen because added keys were handled before
                builder.set(key, newValue.get());
            }
        });

        return builder.isEmpty() ? Optional.empty() : Optional.of(builder.build());
    }

    /**
     * Creates a {@link JsonMergePatch} with an patch object containing the given {@code mergePatch} at the given {@code path}.
     *
     * @param path The path on which the given {@code mergePatch} should be applied later.
     * @param mergePatch the actual patch.
     * @return the merge patch.
     */
    public static JsonMergePatch of(final JsonPointer path, final JsonValue mergePatch) {
        return new JsonMergePatch(JsonFactory.newObject(path, mergePatch));
    }

    /**
     * Creates a {@link JsonMergePatch} with an patch object containing the given {@code mergePatch} at root level.
     *
     * @param mergePatch the actual patch.
     * @return the merge patch.
     */
    public static JsonMergePatch of(final JsonValue mergePatch) {
        return new JsonMergePatch(mergePatch);
    }

    /**
     * Merge 2 JSON values recursively into one. In case of conflict, the first value is more important.
     *
     * @param value1 the first json value to merge, overrides conflicting fields.
     * @param value2 the second json value to merge.
     * @return the merged json value.
     */
    private static JsonValue mergeJsonValues(final JsonValue value1, final JsonValue value2) {
        final JsonValue result;
        if (value1.isObject() && value2.isObject()) {
            result = mergeJsonObjects(value1.asObject(), value2.asObject());
        } else {
            if (value1.isObject()) {
                result = value1.asObject().filter(field -> !field.getValue().isNull());
            } else {
                result = value1;
            }
        }

        return result;
    }

    private static JsonObject mergeJsonObjects(final JsonObject jsonObject1, final JsonObject jsonObject2) {

        if (jsonObject1.isNull()) {
            return JsonFactory.nullObject();
        }

        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        // add fields of jsonObject1
        jsonObject1.forEach(jsonField -> {
            final JsonKey key = jsonField.getKey();
            final JsonValue value1 = jsonField.getValue();
            final Optional<JsonValue> maybeValue2 = jsonObject2.getValue(key);

            if (value1.isNull()) {
                return;
            }
            if (maybeValue2.isPresent()) {
                builder.set(key, mergeJsonValues(value1, maybeValue2.get()));
            } else {
                if (value1.isObject()) {
                    builder.set(key, value1.asObject().filter(field -> !field.getValue().isNull()));
                } else {
                    builder.set(jsonField);
                }
            }
        });

        // add fields of jsonObject2 not present in jsonObject1
        jsonObject2.forEach(jsonField -> {
            if (!jsonObject1.contains(jsonField.getKey())) {
                builder.set(jsonField);
            }
        });

        return builder.build();
    }

    /**
     * Applies this merge patch on the given json value.
     *
     * @param jsonValue the json value that should be patched.
     * @return the patched json value.
     */
    public JsonValue applyOn(final JsonValue jsonValue) {
        return mergeJsonValues(mergePatch, jsonValue);
    }

    /**
     * @return the merge patch json value
     */
    public JsonValue asJsonValue() {
        return mergePatch;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final JsonMergePatch that = (JsonMergePatch) o;
        return Objects.equals(mergePatch, that.mergePatch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mergePatch);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "mergePatch=" + mergePatch +
                "]";
    }

}
