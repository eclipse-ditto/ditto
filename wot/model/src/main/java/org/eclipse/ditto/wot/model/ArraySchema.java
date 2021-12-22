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
 * @since 2.4.0
 */
public interface ArraySchema extends SingleDataSchema {

    static ArraySchema fromJson(final JsonObject jsonObject) {
        return new ImmutableArraySchema(jsonObject);
    }

    static ArraySchema.Builder newBuilder() {
        return ArraySchema.Builder.newBuilder();
    }

    static ArraySchema.Builder newBuilder(final JsonObject jsonObject) {
        return ArraySchema.Builder.newBuilder(jsonObject);
    }

    default ArraySchema.Builder toBuilder() {
        return ArraySchema.Builder.newBuilder(toJson());
    }

    @Override
    default Optional<DataSchemaType> getType() {
        return Optional.of(DataSchemaType.ARRAY);
    }

    Optional<DataSchema> getItems();

    Optional<Integer> getMinItems();

    Optional<Integer> getMaxItems();

    interface Builder extends SingleDataSchema.Builder<Builder, ArraySchema> {

        static Builder newBuilder() {
            return new MutableArraySchemaBuilder(JsonObject.newBuilder());
        }

        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableArraySchemaBuilder(jsonObject.toBuilder());
        }

        Builder setItems(@Nullable DataSchema items);

        Builder setMinItems(@Nullable Integer minItems);

        Builder setMaxItems(@Nullable Integer maxItems);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of an ArraySchema.
     */
    @Immutable
    final class JsonFields {

        public static final JsonFieldDefinition<JsonObject> ITEMS = JsonFactory.newJsonObjectFieldDefinition(
                "items");

        public static final JsonFieldDefinition<JsonArray> ITEMS_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "items");

        public static final JsonFieldDefinition<Integer> MIN_ITEMS = JsonFactory.newIntFieldDefinition(
                "minItems");

        public static final JsonFieldDefinition<Integer> MAX_ITEMS = JsonFactory.newIntFieldDefinition(
                "minItems");


        private JsonFields() {
            throw new AssertionError();
        }
    }
}
