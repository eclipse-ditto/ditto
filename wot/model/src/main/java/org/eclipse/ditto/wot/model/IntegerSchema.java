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
 *
 * @since 2.4.0
 */
public interface IntegerSchema extends SingleDataSchema {

    static IntegerSchema fromJson(final JsonObject jsonObject) {
        return new ImmutableIntegerSchema(jsonObject);
    }

    static IntegerSchema.Builder newBuilder() {
        return IntegerSchema.Builder.newBuilder();
    }

    static IntegerSchema.Builder newBuilder(final JsonObject jsonObject) {
        return IntegerSchema.Builder.newBuilder(jsonObject);
    }

    default IntegerSchema.Builder toBuilder() {
        return IntegerSchema.Builder.newBuilder(toJson());
    }

    @Override
    default Optional<DataSchemaType> getType() {
        return Optional.of(DataSchemaType.INTEGER);
    }

    Optional<Integer> getMinimum();

    Optional<Integer> getExclusiveMinimum();

    Optional<Integer> getMaximum();

    Optional<Integer> getExclusiveMaximum();

    Optional<Integer> getMultipleOf();

    interface Builder extends SingleDataSchema.Builder<Builder, IntegerSchema> {

        static Builder newBuilder() {
            return new MutableIntegerSchemaBuilder(JsonObject.newBuilder());
        }

        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableIntegerSchemaBuilder(jsonObject.toBuilder());
        }

        Builder setMinimum(@Nullable Integer minimum);

        Builder setExclusiveMinimum(@Nullable Integer exclusiveMinimum);

        Builder setMaximum(@Nullable Integer maximum);

        Builder setExclusiveMaximum(@Nullable Integer exclusiveMaximum);

        Builder setMultipleOf(@Nullable Integer multipleOf);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of an IntegerSchema.
     */
    @Immutable
    final class JsonFields {

        public static final JsonFieldDefinition<Integer> MINIMUM = JsonFactory.newIntFieldDefinition(
                "minimum");

        public static final JsonFieldDefinition<Integer> EXCLUSIVE_MINIMUM = JsonFactory.newIntFieldDefinition(
                "exclusiveMinimum");

        public static final JsonFieldDefinition<Integer> MAXIMUM = JsonFactory.newIntFieldDefinition(
                "maximum");

        public static final JsonFieldDefinition<Integer> EXCLUSIVE_MAXIMUM = JsonFactory.newIntFieldDefinition(
                "exclusiveMaximum");

        public static final JsonFieldDefinition<Integer> MULTIPLE_OF = JsonFactory.newIntFieldDefinition(
                "multipleOf");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
