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
 * A NullSchema is a {@link SingleDataSchema} describing the JSON {@code null} value.
 * <p>
 * This schema type is used to explicitly indicate that a value must be null.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#nullschema">WoT TD NullSchema</a>
 * @since 2.4.0
 */
public interface NullSchema extends SingleDataSchema {

    /**
     * Creates a new NullSchema from the specified JSON object.
     *
     * @param jsonObject the JSON object representing the null schema.
     * @return the NullSchema.
     */
    static NullSchema fromJson(final JsonObject jsonObject) {
        return new ImmutableNullSchema(jsonObject);
    }

    /**
     * Creates a new builder for building a NullSchema.
     *
     * @return the builder.
     */
    static NullSchema.Builder newBuilder() {
        return NullSchema.Builder.newBuilder();
    }

    /**
     * Creates a new builder for building a NullSchema, initialized with the values from the specified
     * JSON object.
     *
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     */
    static NullSchema.Builder newBuilder(final JsonObject jsonObject) {
        return NullSchema.Builder.newBuilder(jsonObject);
    }

    /**
     * Returns a mutable builder with a fluent API for building a NullSchema, initialized with the values
     * of this instance.
     *
     * @return the builder.
     */
    default NullSchema.Builder toBuilder() {
        return NullSchema.Builder.newBuilder(toJson());
    }

    /**
     * A mutable builder with a fluent API for building a {@link NullSchema}.
     */
    interface Builder extends SingleDataSchema.Builder<Builder, NullSchema> {

        /**
         * Creates a new builder for building a NullSchema.
         *
         * @return the builder.
         */
        static Builder newBuilder() {
            return new MutableNullSchemaBuilder(JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building a NullSchema, initialized with the values from the specified
         * JSON object.
         *
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableNullSchemaBuilder(jsonObject.toBuilder());
        }

    }

    @Override
    default Optional<DataSchemaType> getType() {
        return Optional.of(DataSchemaType.NULL);
    }
}
