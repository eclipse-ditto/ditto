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
package org.eclipse.ditto.wot.model;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * An ArraySchema is a {@link SingleDataSchema} describing the JSON data type {@code array}.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#arrayschema">WoT TD ArraySchema</a>
 * @since 2.4.0
 */
public interface ArraySchema extends SingleDataSchema {

    /**
     * Creates a new ArraySchema from the specified JSON object.
     *
     * @param jsonObject the JSON object representing the array schema.
     * @return the ArraySchema.
     */
    static ArraySchema fromJson(final JsonObject jsonObject) {
        return new ImmutableArraySchema(jsonObject);
    }

    /**
     * Creates a new builder for building an ArraySchema.
     *
     * @return the builder.
     */
    static ArraySchema.Builder newBuilder() {
        return ArraySchema.Builder.newBuilder();
    }

    /**
     * Creates a new builder for building an ArraySchema, initialized with the values from the specified
     * JSON object.
     *
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     */
    static ArraySchema.Builder newBuilder(final JsonObject jsonObject) {
        return ArraySchema.Builder.newBuilder(jsonObject);
    }

    /**
     * Returns a mutable builder with a fluent API for building an ArraySchema, initialized with the values
     * of this instance.
     *
     * @return the builder.
     */
    default ArraySchema.Builder toBuilder() {
        return ArraySchema.Builder.newBuilder(toJson());
    }

    @Override
    default Optional<DataSchemaType> getType() {
        return Optional.of(DataSchemaType.ARRAY);
    }

    /**
     * Returns the optional data schema describing the elements of the array.
     * <p>
     * May be a single schema (all elements have the same type) or a multiple schema (tuple validation).
     * </p>
     *
     * @return the optional items schema.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#arrayschema">WoT TD ArraySchema (items)</a>
     */
    Optional<DataSchema> getItems();

    /**
     * Returns the optional minimum number of items the array must contain.
     *
     * @return the optional minimum items count.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#arrayschema">WoT TD ArraySchema (minItems)</a>
     */
    Optional<Integer> getMinItems();

    /**
     * Returns the optional maximum number of items the array may contain.
     *
     * @return the optional maximum items count.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#arrayschema">WoT TD ArraySchema (maxItems)</a>
     */
    Optional<Integer> getMaxItems();

    /**
     * A mutable builder with a fluent API for building an {@link ArraySchema}.
     */
    interface Builder extends SingleDataSchema.Builder<Builder, ArraySchema> {

        /**
         * Creates a new builder for building an ArraySchema.
         *
         * @return the builder.
         */
        static Builder newBuilder() {
            return new MutableArraySchemaBuilder(JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building an ArraySchema, initialized with the values from the specified
         * JSON object.
         *
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableArraySchemaBuilder(jsonObject.toBuilder());
        }

        /**
         * Sets the items schema.
         *
         * @param items the items schema, or {@code null} to remove.
         * @return this builder.
         */
        Builder setItems(@Nullable DataSchema items);

        /**
         * Sets the minimum items constraint.
         *
         * @param minItems the minimum items count, or {@code null} to remove.
         * @return this builder.
         */
        Builder setMinItems(@Nullable Integer minItems);

        /**
         * Sets the maximum items constraint.
         *
         * @param maxItems the maximum items count, or {@code null} to remove.
         * @return this builder.
         */
        Builder setMaxItems(@Nullable Integer maxItems);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of an ArraySchema.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the items schema (single schema).
         */
        public static final JsonFieldDefinition<JsonObject> ITEMS = JsonFactory.newJsonObjectFieldDefinition(
                "items");

        /**
         * JSON field definition for the items schema (multiple schemas for tuple validation).
         */
        public static final JsonFieldDefinition<JsonArray> ITEMS_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "items");

        /**
         * JSON field definition for the minimum items count.
         */
        public static final JsonFieldDefinition<Integer> MIN_ITEMS = JsonFactory.newIntFieldDefinition(
                "minItems");

        /**
         * JSON field definition for the maximum items count.
         */
        public static final JsonFieldDefinition<Integer> MAX_ITEMS = JsonFactory.newIntFieldDefinition(
                "minItems");


        private JsonFields() {
            throw new AssertionError();
        }
    }
}
