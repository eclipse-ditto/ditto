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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
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
     * @return a JSON merge patch according to <a href="https://datatracker.ietf.org/doc/html/rfc7386">RFC 7386</a>
     * or empty if values are equal.
     */
    public static Optional<JsonMergePatch> compute(final JsonValue oldValue, final JsonValue newValue) {
        return compute(oldValue, newValue, true);
    }

    /**
     * This method computes the change from the given {@code oldValue} to the given {@code newValue}.
     * The result is a JSON merge patch according to <a href="https://datatracker.ietf.org/doc/html/rfc7386">RFC 7386</a>.
     *
     * @param oldValue the original value
     * @param newValue the new changed value
     * @param deleteMissingFieldsWithNull whether to delete fields contained in {@code oldValue} by setting those to
     * {@code null} as defined in RFC 7386. This is the default behavior in order to create a valid JSON merge patch.
     * @return a JSON merge patch according to <a href="https://datatracker.ietf.org/doc/html/rfc7386">RFC 7386</a>
     * or empty if values are equal.
     * @since 3.4.0
     */
    public static Optional<JsonMergePatch> compute(final JsonValue oldValue, final JsonValue newValue,
            final boolean deleteMissingFieldsWithNull) {
        return computeForValue(oldValue, newValue, deleteMissingFieldsWithNull).map(JsonMergePatch::of);
    }

    private static Optional<JsonValue> computeForValue(final JsonValue oldValue, final JsonValue newValue,
            final boolean deleteMissingFieldsWithNull) {
        @Nullable final JsonValue diff;
        if (oldValue.equals(newValue)) {
            diff = null;
        } else if (oldValue.isObject() && newValue.isObject()) {
            diff = computeForObject(oldValue.asObject(), newValue.asObject(), deleteMissingFieldsWithNull)
                    .orElse(null);
        } else {
            diff = newValue;
        }
        return Optional.ofNullable(diff);
    }

    private static Optional<JsonObject> computeForObject(final JsonObject oldJsonObject,
            final JsonObject newJsonObject,
            final boolean deleteMissingFieldsWithNull) {
        final JsonObjectBuilder builder = JsonObject.newBuilder();
        final List<JsonKey> oldKeys = oldJsonObject.getKeys();
        final List<JsonKey> newKeys = newJsonObject.getKeys();

        final List<JsonKey> addedKeys = newKeys.stream()
                .filter(key -> !oldKeys.contains(key))
                .collect(Collectors.toList());
        addedKeys.forEach(key -> newJsonObject.getValue(key).ifPresent(value -> builder.set(key, value)));

        if (deleteMissingFieldsWithNull) {
            final List<JsonKey> deletedKeys = oldKeys.stream()
                    .filter(key -> !newKeys.contains(key))
                    .collect(Collectors.toList());
            deletedKeys.forEach(key -> builder.set(key, JsonValue.nullLiteral()));
        }

        final List<JsonKey> keptKeys = oldKeys.stream()
                .filter(newKeys::contains)
                .collect(Collectors.toList());
        keptKeys.forEach(key -> {
            final Optional<JsonValue> oldValue = oldJsonObject.getValue(key);
            final Optional<JsonValue> newValue = newJsonObject.getValue(key);
            if (oldValue.isPresent() && newValue.isPresent()) {
                computeForValue(oldValue.get(), newValue.get(), deleteMissingFieldsWithNull)
                        .ifPresent(diff -> builder.set(key, diff));
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
     * Creates a {@link JsonMergePatch} with a patch object containing the given {@code mergePatch} at the given {@code path}.
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

        final JsonObjectBuilder builder = jsonObject2.toBuilder();
        // add fields of jsonObject1
        jsonObject1.forEach(jsonField -> {
            final JsonKey key = jsonField.getKey();
            final JsonValue value1 = jsonField.getValue();
            final Optional<JsonValue> maybeValue2 = jsonObject2.getValue(key);

            if (value1.isNull()) {
                builder.remove(key);
            } else if (maybeValue2.isPresent()) {
                builder.set(key, mergeJsonValues(value1, maybeValue2.get()));
            } else {
                if (value1.isObject()) {
                    builder.set(key, value1.asObject().filter(field -> !field.getValue().isNull()));
                } else {
                    builder.set(jsonField);
                }
            }
        });

        final List<JsonKey> toBeNulledKeysByRegex = determineToBeNulledKeysByRegex(jsonObject1, jsonObject2);

        // add fields of jsonObject2 not present in jsonObject1
        jsonObject2.forEach(jsonField -> {
            if (toBeNulledKeysByRegex.contains(jsonField.getKey())) {
                builder.remove(jsonField.getKey());
                jsonObject1.getValue(jsonField.getKey())
                        .ifPresent(v -> builder.set(jsonField.getKey(), v));
            }
        });

        return builder.build();
    }

    private static List<JsonKey> determineToBeNulledKeysByRegex(
            final JsonObject jsonObject1,
            final JsonObject jsonObject2) {

        final List<JsonKey> toBeNulledKeysByRegex = new ArrayList<>();
        jsonObject1.forEach(jsonField -> {
            if (isDeletionByRegex(jsonField.getKey(), jsonField.getValue())) {
                final Pattern pattern = Pattern.compile(extractDeletionRegex(jsonField.getKey()));
                jsonObject2.getKeys().stream()
                        .filter(key -> pattern.matcher(key).matches())
                        .forEach(toBeNulledKeysByRegex::add);
            }
        });
        return toBeNulledKeysByRegex;
    }

    /**
     * Determines whether the given field of a merge patch deletes all keys of its enclosing JSON object matching a
     * regular expression. This is the case if the key has the form {@code {{ ~<regex>~ }}} or
     * {@code {{ /<regex>/ }}} and the value is {@code null}.
     * <p>
     * Applying a merge patch containing such a field modifies all keys of the enclosing JSON object matching the
     * regular expression, so authorizing such a merge patch must take the enclosing JSON object into account.
     * </p>
     *
     * @param key the key of the merge patch field.
     * @param value the value of the merge patch field.
     * @return {@code true} if the field deletes keys of its enclosing JSON object by a regular expression.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 3.9.9
     */
    public static boolean isDeletionByRegex(final JsonKey key, final JsonValue value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        return value.isNull() && extractDeletionRegex(key) != null;
    }

    @Nullable
    private static String extractDeletionRegex(final JsonKey key) {
        final String keyString = key.toString();
        if (keyString.startsWith("{{") && keyString.endsWith("}}")) {
            final String keyRegexWithoutCurly = keyString.substring(2, keyString.length() - 2).trim();
            if (keyRegexWithoutCurly.length() >= 2 && isEnclosedByRegexDelimiter(keyRegexWithoutCurly)) {
                return keyRegexWithoutCurly.substring(1, keyRegexWithoutCurly.length() - 1);
            }
        }
        return null;
    }

    private static boolean isEnclosedByRegexDelimiter(final String keyRegexWithoutCurly) {
        return keyRegexWithoutCurly.startsWith("~") && keyRegexWithoutCurly.endsWith("~") ||
                keyRegexWithoutCurly.startsWith("/") && keyRegexWithoutCurly.endsWith("/");
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
