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

import static org.eclipse.ditto.json.JsonFactory.newFieldDefinition;

import java.util.Optional;

/**
 * Defines a JSON Patch which can be used to specify modifications on JSON Objects. For example, the following
 * patch defines an insertion operation on path "address/city" with value "Berlin".
 * <p>
 * <pre>
 *    {
 *       "op": "add",
 *       "path": "address/city",
 *       "value": "Berlin"
 *    }
 * </pre>
 * <p>
 * <em>Implementations of this interface are required to be immutable!</em>
 * </p>
 */
public interface JsonPatch {

    /**
     * Returns a new JSON Patch which can be used to specify modifications on JSON Objects.
     *
     * @param operation the patch operation type
     * @param path a JSON Pointer specifying the path within the JSON Object on which the operation is defined
     * @param value the value to be used for the specified operation on the given path
     * @return the new JSON Patch.
     * @throws NullPointerException if {@code operation} or {@code path} is {@code null}.
     */
    static JsonPatch newInstance(final JsonPatch.Operation operation, final JsonPointer path, final JsonValue value) {
        return JsonFactory.newPatch(operation, path, value);
    }

    /**
     * Returns the JSON Patch operation type.
     *
     * @return {@code Operation}
     */
    Operation getOperation();

    /**
     * Returns the path within the JSON Object on which the operation is defined.
     *
     * @return the path.
     */
    JsonPointer getPath();

    /**
     * Returns the value to be used for the specified operation on the given path, if applicable, or an empty
     * {@code Optional} otherwise.
     *
     * @return the value.
     */
    Optional<JsonValue> getValue();

    /**
     * Returns the JSON object representation of this JSON Patch.
     *
     * @return the JSON object representation of this patch.
     */
    JsonObject toJson();

    /**
     * Returns the JSON string for this JSON Patch in its minimal form, without any additional whitespace.
     *
     * @return a JSON string that represents this JSON Patch.
     */
    @Override
    String toString();

    /**
     * Enumeration defining the supported JSON Patch operation types.
     *
     */
    enum Operation {
        /**
         * Used to define additions/insertions on a path.
         */
        ADD("add"),

        /**
         * Used to define deletions on a path.
         */
        REMOVE("remove"),

        /**
         * Used to change values assigned to a path.
         */
        REPLACE("replace");

        private final String name;

        Operation(final String name) {
            this.name = name;
        }

        /**
         * Defines the reverse operation of #toString method to be able to convert a string representation of an
         * operation to its corresponding enumeration element.
         *
         * @param name the operation name
         * @return {@code Operation}
         */
        public static Operation fromString(final String name) {
            return valueOf(name.toUpperCase());
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * An enumeration of the known {@link JsonField}s of a Thing.
     *
     */
    final class JsonFields {

        /**
         * JSON field containing the JSON Patch's operation.
         */
        public static final JsonFieldDefinition OPERATION = newFieldDefinition("op", String.class);

        /**
         * JSON field containing the JSON Patch's path.
         */
        public static final JsonFieldDefinition PATH = newFieldDefinition("path", String.class);

        /**
         * JSON field containing the JSON Patch's value.
         */
        public static final JsonFieldDefinition VALUE = newFieldDefinition("value", JsonValue.class);

        private JsonFields() {
            throw new AssertionError();
        }
    }

}
