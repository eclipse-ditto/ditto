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
 * A NumberSchema is a {@link SingleDataSchema} describing the JSON data type {@code number}.
 * <p>
 * Number values are floating-point numbers (including integers).
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#numberschema">WoT TD NumberSchema</a>
 * @since 2.4.0
 */
public interface NumberSchema extends SingleDataSchema {

    /**
     * Creates a new NumberSchema from the specified JSON object.
     *
     * @param jsonObject the JSON object representing the number schema.
     * @return the NumberSchema.
     */
    static NumberSchema fromJson(final JsonObject jsonObject) {
        return new ImmutableNumberSchema(jsonObject);
    }

    /**
     * Creates a new builder for building a NumberSchema.
     *
     * @return the builder.
     */
    static NumberSchema.Builder newBuilder() {
        return NumberSchema.Builder.newBuilder();
    }

    /**
     * Creates a new builder for building a NumberSchema, initialized with the values from the specified
     * JSON object.
     *
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     */
    static NumberSchema.Builder newBuilder(final JsonObject jsonObject) {
        return NumberSchema.Builder.newBuilder(jsonObject);
    }

    /**
     * Returns a mutable builder with a fluent API for building a NumberSchema, initialized with the values
     * of this instance.
     *
     * @return the builder.
     */
    default NumberSchema.Builder toBuilder() {
        return NumberSchema.Builder.newBuilder(toJson());
    }

    @Override
    default Optional<DataSchemaType> getType() {
        return Optional.of(DataSchemaType.NUMBER);
    }

    /**
     * Returns the optional inclusive minimum value the number must have.
     *
     * @return the optional minimum value.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#numberschema">WoT TD NumberSchema (minimum)</a>
     */
    Optional<Double> getMinimum();

    /**
     * Returns the optional exclusive minimum value the number must exceed.
     *
     * @return the optional exclusive minimum value.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#numberschema">WoT TD NumberSchema (exclusiveMinimum)</a>
     */
    Optional<Double> getExclusiveMinimum();

    /**
     * Returns the optional inclusive maximum value the number may have.
     *
     * @return the optional maximum value.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#numberschema">WoT TD NumberSchema (maximum)</a>
     */
    Optional<Double> getMaximum();

    /**
     * Returns the optional exclusive maximum value the number must be less than.
     *
     * @return the optional exclusive maximum value.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#numberschema">WoT TD NumberSchema (exclusiveMaximum)</a>
     */
    Optional<Double> getExclusiveMaximum();

    /**
     * Returns the optional value that the number must be a multiple of.
     *
     * @return the optional multiple-of constraint.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#numberschema">WoT TD NumberSchema (multipleOf)</a>
     */
    Optional<Double> getMultipleOf();

    /**
     * A mutable builder with a fluent API for building a {@link NumberSchema}.
     */
    interface Builder extends SingleDataSchema.Builder<Builder, NumberSchema> {

        /**
         * Creates a new builder for building a NumberSchema.
         *
         * @return the builder.
         */
        static Builder newBuilder() {
            return new MutableNumberSchemaBuilder(JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building a NumberSchema, initialized with the values from the specified
         * JSON object.
         *
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableNumberSchemaBuilder(jsonObject.toBuilder());
        }

        /**
         * Sets the inclusive minimum value constraint.
         *
         * @param minimum the minimum value, or {@code null} to remove.
         * @return this builder.
         */
        Builder setMinimum(@Nullable Double minimum);

        /**
         * Sets the exclusive minimum value constraint.
         *
         * @param exclusiveMinimum the exclusive minimum value, or {@code null} to remove.
         * @return this builder.
         */
        Builder setExclusiveMinimum(@Nullable Double exclusiveMinimum);

        /**
         * Sets the inclusive maximum value constraint.
         *
         * @param maximum the maximum value, or {@code null} to remove.
         * @return this builder.
         */
        Builder setMaximum(@Nullable Double maximum);

        /**
         * Sets the exclusive maximum value constraint.
         *
         * @param exclusiveMaximum the exclusive maximum value, or {@code null} to remove.
         * @return this builder.
         */
        Builder setExclusiveMaximum(@Nullable Double exclusiveMaximum);

        /**
         * Sets the multiple-of constraint.
         *
         * @param multipleOf the multiple-of value, or {@code null} to remove.
         * @return this builder.
         */
        Builder setMultipleOf(@Nullable Double multipleOf);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a NumberSchema.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the inclusive minimum value constraint.
         */
        public static final JsonFieldDefinition<Double> MINIMUM = JsonFactory.newDoubleFieldDefinition(
                "minimum");

        /**
         * JSON field definition for the exclusive minimum value constraint.
         */
        public static final JsonFieldDefinition<Double> EXCLUSIVE_MINIMUM = JsonFactory.newDoubleFieldDefinition(
                "exclusiveMinimum");

        /**
         * JSON field definition for the inclusive maximum value constraint.
         */
        public static final JsonFieldDefinition<Double> MAXIMUM = JsonFactory.newDoubleFieldDefinition(
                "maximum");

        /**
         * JSON field definition for the exclusive maximum value constraint.
         */
        public static final JsonFieldDefinition<Double> EXCLUSIVE_MAXIMUM = JsonFactory.newDoubleFieldDefinition(
                "exclusiveMaximum");

        /**
         * JSON field definition for the multiple-of constraint.
         */
        public static final JsonFieldDefinition<Double> MULTIPLE_OF = JsonFactory.newDoubleFieldDefinition(
                "multipleOf");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
