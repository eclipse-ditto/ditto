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

import org.eclipse.ditto.json.JsonObject;

/**
 * A BooleanSchema is a {@link SingleDataSchema} describing the JSON data type {@code boolean}.
 * <p>
 * Boolean values can only be {@code true} or {@code false}.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#booleanschema">WoT TD BooleanSchema</a>
 * @since 2.4.0
 */
public interface BooleanSchema extends SingleDataSchema {

    /**
     * Creates a new BooleanSchema from the specified JSON object.
     *
     * @param jsonObject the JSON object representing the boolean schema.
     * @return the BooleanSchema.
     */
    static BooleanSchema fromJson(final JsonObject jsonObject) {
        return new ImmutableBooleanSchema(jsonObject);
    }

    /**
     * Creates a new builder for building a BooleanSchema.
     *
     * @return the builder.
     */
    static BooleanSchema.Builder newBuilder() {
        return BooleanSchema.Builder.newBuilder();
    }

    /**
     * Creates a new builder for building a BooleanSchema, initialized with the values from the specified
     * JSON object.
     *
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     */
    static BooleanSchema.Builder newBuilder(final JsonObject jsonObject) {
        return BooleanSchema.Builder.newBuilder(jsonObject);
    }

    /**
     * Returns a mutable builder with a fluent API for building a BooleanSchema, initialized with the values
     * of this instance.
     *
     * @return the builder.
     */
    default BooleanSchema.Builder toBuilder() {
        return BooleanSchema.Builder.newBuilder(toJson());
    }

    /**
     * A mutable builder with a fluent API for building a {@link BooleanSchema}.
     */
    interface Builder extends SingleDataSchema.Builder<Builder, BooleanSchema> {

        /**
         * Creates a new builder for building a BooleanSchema.
         *
         * @return the builder.
         */
        static Builder newBuilder() {
            return new MutableBooleanSchemaBuilder(JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building a BooleanSchema, initialized with the values from the specified
         * JSON object.
         *
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableBooleanSchemaBuilder(jsonObject.toBuilder());
        }

    }

    @Override
    default Optional<DataSchemaType> getType() {
        return Optional.of(DataSchemaType.BOOLEAN);
    }
}
