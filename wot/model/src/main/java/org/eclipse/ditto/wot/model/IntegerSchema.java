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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * An IntegerSchema is a {@link SingleDataSchema} describing the JSON data type {@code integer}.
 * <p>
 * Integer values are whole numbers without a fractional part.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#integerschema">WoT TD IntegerSchema</a>
 * @since 2.4.0
 */
public interface IntegerSchema extends SingleDataSchema {

    /**
     * Creates a new IntegerSchema from the specified JSON object.
     *
     * @param jsonObject the JSON object representing the integer schema.
     * @return the IntegerSchema.
     */
    static IntegerSchema fromJson(final JsonObject jsonObject) {
        return new ImmutableIntegerSchema(jsonObject);
    }

    /**
     * Creates a new builder for building an IntegerSchema.
     *
     * @return the builder.
     */
    static IntegerSchema.Builder newBuilder() {
        return IntegerSchema.Builder.newBuilder();
    }

    /**
     * Creates a new builder for building an IntegerSchema, initialized with the values from the specified
     * JSON object.
     *
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     */
    static IntegerSchema.Builder newBuilder(final JsonObject jsonObject) {
        return IntegerSchema.Builder.newBuilder(jsonObject);
    }

    /**
     * Returns a mutable builder with a fluent API for building an IntegerSchema, initialized with the values
     * of this instance.
     *
     * @return the builder.
     */
    default IntegerSchema.Builder toBuilder() {
        return IntegerSchema.Builder.newBuilder(toJson());
    }

    @Override
    default Optional<DataSchemaType> getType() {
        return Optional.of(DataSchemaType.INTEGER);
    }

    /**
     * Returns the optional inclusive minimum value the integer must have.
     *
     * @return the optional minimum value.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#integerschema">WoT TD IntegerSchema (minimum)</a>
     */
    Optional<Integer> getMinimum();

    /**
     * Returns the optional exclusive minimum value the integer must exceed.
     *
     * @return the optional exclusive minimum value.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#integerschema">WoT TD IntegerSchema (exclusiveMinimum)</a>
     */
    Optional<Integer> getExclusiveMinimum();

    /**
     * Returns the optional inclusive maximum value the integer may have.
     *
     * @return the optional maximum value.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#integerschema">WoT TD IntegerSchema (maximum)</a>
     */
    Optional<Integer> getMaximum();

    /**
     * Returns the optional exclusive maximum value the integer must be less than.
     *
     * @return the optional exclusive maximum value.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#integerschema">WoT TD IntegerSchema (exclusiveMaximum)</a>
     */
    Optional<Integer> getExclusiveMaximum();

    /**
     * Returns the optional value that the integer must be a multiple of.
     *
     * @return the optional multiple-of constraint.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#integerschema">WoT TD IntegerSchema (multipleOf)</a>
     */
    Optional<Integer> getMultipleOf();

    /**
     * A mutable builder with a fluent API for building an {@link IntegerSchema}.
     */
    interface Builder extends SingleDataSchema.Builder<Builder, IntegerSchema> {

        /**
         * Creates a new builder for building an IntegerSchema.
         *
         * @return the builder.
         */
        static Builder newBuilder() {
            return new MutableIntegerSchemaBuilder(JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building an IntegerSchema, initialized with the values from the specified
         * JSON object.
         *
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableIntegerSchemaBuilder(jsonObject.toBuilder());
        }

        /**
         * Sets the inclusive minimum value constraint.
         *
         * @param minimum the minimum value, or {@code null} to remove.
         * @return this builder.
         */
        Builder setMinimum(@Nullable Integer minimum);

        /**
         * Sets the exclusive minimum value constraint.
         *
         * @param exclusiveMinimum the exclusive minimum value, or {@code null} to remove.
         * @return this builder.
         */
        Builder setExclusiveMinimum(@Nullable Integer exclusiveMinimum);

        /**
         * Sets the inclusive maximum value constraint.
         *
         * @param maximum the maximum value, or {@code null} to remove.
         * @return this builder.
         */
        Builder setMaximum(@Nullable Integer maximum);

        /**
         * Sets the exclusive maximum value constraint.
         *
         * @param exclusiveMaximum the exclusive maximum value, or {@code null} to remove.
         * @return this builder.
         */
        Builder setExclusiveMaximum(@Nullable Integer exclusiveMaximum);

        /**
         * Sets the multiple-of constraint.
         *
         * @param multipleOf the multiple-of value, or {@code null} to remove.
         * @return this builder.
         */
        Builder setMultipleOf(@Nullable Integer multipleOf);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of an IntegerSchema.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the inclusive minimum value constraint.
         */
        public static final JsonFieldDefinition<Integer> MINIMUM = JsonFactory.newIntFieldDefinition(
                "minimum");

        /**
         * JSON field definition for the exclusive minimum value constraint.
         */
        public static final JsonFieldDefinition<Integer> EXCLUSIVE_MINIMUM = JsonFactory.newIntFieldDefinition(
                "exclusiveMinimum");

        /**
         * JSON field definition for the inclusive maximum value constraint.
         */
        public static final JsonFieldDefinition<Integer> MAXIMUM = JsonFactory.newIntFieldDefinition(
                "maximum");

        /**
         * JSON field definition for the exclusive maximum value constraint.
         */
        public static final JsonFieldDefinition<Integer> EXCLUSIVE_MAXIMUM = JsonFactory.newIntFieldDefinition(
                "exclusiveMaximum");

        /**
         * JSON field definition for the multiple-of constraint.
         */
        public static final JsonFieldDefinition<Integer> MULTIPLE_OF = JsonFactory.newIntFieldDefinition(
                "multipleOf");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
