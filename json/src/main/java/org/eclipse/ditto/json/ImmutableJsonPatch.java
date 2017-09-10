/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.json;

import static java.util.Objects.requireNonNull;

import java.util.Optional;

/**
 * Immutable default implementation of {@link JsonPatch}.
 */
final class ImmutableJsonPatch implements JsonPatch {

    private final Operation operation;
    private final JsonPointer path;
    private final JsonValue value;

    private ImmutableJsonPatch(final Operation operation, final JsonPointer path, final JsonValue value) {
        this.operation = requireNonNull(operation, "The operation of the JSON Patch must not be null!");
        this.path = requireNonNull(path, "The path of the JSON Patch must not be null!");
        this.value = value;
    }

    /**
     * Returns a new JSON Patch which can be used to specify modifications on JSON Objects.
     *
     * @param operation the patch operation type
     * @param path a JSON Pointer specifying the path within the JSON Object on which the operation is defined
     * @param value the value to be used for the specified operation on the given path
     * @return the new JSON Patch.
     * @throws NullPointerException if {@code operation} or {@code path} is {@code null}.
     */
    public static ImmutableJsonPatch of(final JsonPatch.Operation operation, final JsonPointer path,
            final JsonValue value) {
        return new ImmutableJsonPatch(operation, path, value);
    }

    /**
     * Returns a new JSON Patch created from the given string.
     *
     * @param jsonString the string representation of the JSON Patch object to be created.
     * @return the new JSON Patch.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws JsonParseException if {@code jsonString} does not contain a valid JSON Patch JSON object.
     */
    public static ImmutableJsonPatch fromJson(final String jsonString) {
        requireNonNull(jsonString, "The string representation of the JSON Patch to be created must not be null!");
        if (jsonString.isEmpty()) {
            throw new IllegalArgumentException(
                    "The string representation of the JSON Patch to be created must not be empty!");
        }

        final JsonObject jsonObject = JsonFactory.newObject(jsonString);
        final JsonObjectReader reader = JsonReader.from(jsonObject);

        final Operation operation = Operation.fromString(reader.get(JsonFields.OPERATION));
        final JsonPointer path = JsonFactory.newPointer(reader.get(JsonFields.PATH));
        final JsonValue value = jsonObject.getValue(JsonFields.VALUE).orElse(null);
        return new ImmutableJsonPatch(operation, path, value);
    }

    @Override
    public Operation getOperation() {
        return operation;
    }

    @Override
    public JsonPointer getPath() {
        return path;
    }

    @Override
    public Optional<JsonValue> getValue() {
        return Optional.ofNullable(value);
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder() //
                .set(JsonFields.OPERATION, operation.toString()) //
                .set(JsonFields.PATH, path.toString());

        getValue().ifPresent(presentValue -> builder.set(JsonFields.VALUE, presentValue));
        return builder.build();
    }

    @Override
    public String toString() {
        return toJson().toString();
    }
}
