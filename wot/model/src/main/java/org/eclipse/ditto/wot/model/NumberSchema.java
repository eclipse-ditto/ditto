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
 *
 * @since 2.4.0
 */
public interface NumberSchema extends SingleDataSchema {

    static NumberSchema fromJson(final JsonObject jsonObject) {
        return new ImmutableNumberSchema(jsonObject);
    }

    static NumberSchema.Builder newBuilder() {
        return NumberSchema.Builder.newBuilder();
    }

    static NumberSchema.Builder newBuilder(final JsonObject jsonObject) {
        return NumberSchema.Builder.newBuilder(jsonObject);
    }

    default NumberSchema.Builder toBuilder() {
        return NumberSchema.Builder.newBuilder(toJson());
    }

    @Override
    default Optional<DataSchemaType> getType() {
        return Optional.of(DataSchemaType.NUMBER);
    }

    Optional<Double> getMinimum();

    Optional<Double> getExclusiveMinimum();

    Optional<Double> getMaximum();

    Optional<Double> getExclusiveMaximum();

    Optional<Double> getMultipleOf();

    interface Builder extends SingleDataSchema.Builder<Builder, NumberSchema> {

        static Builder newBuilder() {
            return new MutableNumberSchemaBuilder(JsonObject.newBuilder());
        }

        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableNumberSchemaBuilder(jsonObject.toBuilder());
        }

        Builder setMinimum(@Nullable Double minimum);

        Builder setExclusiveMinimum(@Nullable Double exclusiveMinimum);

        Builder setMaximum(@Nullable Double maximum);

        Builder setExclusiveMaximum(@Nullable Double exclusiveMaximum);

        Builder setMultipleOf(@Nullable Double multipleOf);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a NumberSchema.
     */
    @Immutable
    final class JsonFields {

        public static final JsonFieldDefinition<Double> MINIMUM = JsonFactory.newDoubleFieldDefinition(
                "minimum");

        public static final JsonFieldDefinition<Double> EXCLUSIVE_MINIMUM = JsonFactory.newDoubleFieldDefinition(
                "exclusiveMinimum");

        public static final JsonFieldDefinition<Double> MAXIMUM = JsonFactory.newDoubleFieldDefinition(
                "maximum");

        public static final JsonFieldDefinition<Double> EXCLUSIVE_MAXIMUM = JsonFactory.newDoubleFieldDefinition(
                "exclusiveMaximum");

        public static final JsonFieldDefinition<Double> MULTIPLE_OF = JsonFactory.newDoubleFieldDefinition(
                "multipleOf");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
