/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import static java.util.Objects.requireNonNull;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Immutable default implementation of {@link JsonPatch}.
 */
@Immutable
final class ImmutableJsonPatch implements JsonPatch {

    private final Operation operation;
    private final JsonPointer path;
    @Nullable private final JsonValue value;

    private ImmutableJsonPatch(final Operation operation, final JsonPointer path, @Nullable  final JsonValue value) {
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
    public static ImmutableJsonPatch newInstance(final Operation operation, final JsonPointer path,
            @Nullable final JsonValue value) {

        return new ImmutableJsonPatch(operation, path, value);
    }

    /**
     * Returns a new JSON Patch created from the given string.
     *
     * @param jsonString the string representation of the JSON Patch object to be created.
     * @return the new JSON Patch.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty or if {@code jsonString} did contain an
     * unknown operation.
     * @throws JsonParseException if {@code jsonString} does not contain a valid JSON Patch JSON object.
     * @throws JsonMissingFieldException if {@code jsonString} did not contain {@link JsonFields#OPERATION} or
     * {@link JsonFields#PATH}.
     */
    public static ImmutableJsonPatch fromJson(final String jsonString) {
        requireNonNull(jsonString, "The string representation of the JSON Patch to be created must not be null!");
        if (jsonString.isEmpty()) {
            throw new IllegalArgumentException(
                    "The string representation of the JSON Patch to be created must not be empty!");
        }

        final JsonObject jsonObject = JsonFactory.newObject(jsonString);

        final String operationName = jsonObject.getValueOrThrow(JsonFields.OPERATION);
        final Operation operation = Operation.fromString(operationName).orElseThrow(
                () -> new IllegalArgumentException(MessageFormat.format("Operation <{0}> is unknown!", operationName))
        );
        final JsonPointer path = JsonFactory.newPointer(jsonObject.getValueOrThrow(JsonFields.PATH));
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
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder()
                .set(JsonFields.OPERATION, operation.toString())
                .set(JsonFields.PATH, path.toString());
        if (null != value) {
            builder.set(JsonFields.VALUE, value);
        }

        return builder.build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableJsonPatch that = (ImmutableJsonPatch) o;
        return operation == that.operation &&
                Objects.equals(path, that.path) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operation, path, value);
    }

    @Override
    public String toString() {
        return toJson().toString();
    }

}
