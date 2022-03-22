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
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/**
 * Can be used to get the diff in form of a JSON merge Patch according to
 * <a href="https://datatracker.ietf.org/doc/html/rfc7386">RFC 7386</a> between two {@link JsonValue json values}.
 *
 * @since 2.4.0
 */
public final class JsonMergePatch {

    private JsonMergePatch() {
        throw new AssertionError();
    }

    /**
     * This method computes the change from the given {@code oldValue} to the given {@code newValue}.
     * The result is a JSON merge patch according to <a href="https://datatracker.ietf.org/doc/html/rfc7386">RFC 7386</a>.
     *
     * @param oldValue the original value
     * @param newValue the new changed value
     * @return a JSON merge patch according to <a href="https://datatracker.ietf.org/doc/html/rfc7386">RFC 7386</a> or empty if values are equal.
     * @since 2.4.0
     */
    public static Optional<JsonValue> compute(final JsonValue oldValue, final JsonValue newValue) {
        @Nullable final JsonValue diff;
        if (oldValue.equals(newValue)) {
            diff = null;
        } else if (oldValue.isObject() && newValue.isObject()) {
            diff = compute(oldValue.asObject(), newValue.asObject()).orElse(null);
        } else {
            diff = newValue;
        }
        return Optional.ofNullable(diff);
    }

    /**
     * This method computes the change from the given {@code oldValue} to the given {@code newValue}.
     * The result is a JSON merge patch according to <a href="https://datatracker.ietf.org/doc/html/rfc7386">RFC 7386</a>.
     *
     * @param oldJsonObject the original JSON object
     * @param newJsonObject the new changed JSON object
     * @return a JSON merge patch according to <a href="https://datatracker.ietf.org/doc/html/rfc7386">RFC 7386</a> or empty if values are equal.
     * @since 2.4.0
     */
    public static Optional<JsonObject> compute(final JsonObject oldJsonObject, final JsonObject newJsonObject) {
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
                compute(oldValue.get(), newValue.get()).ifPresent(diff -> builder.set(key, diff));
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

}
