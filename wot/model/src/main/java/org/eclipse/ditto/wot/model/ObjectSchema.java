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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * An ObjectSchema is a {@link SingleDataSchema} describing the JSON data type {@code object}.
 *
 * @since 2.4.0
 */
public interface ObjectSchema extends SingleDataSchema {

    static ObjectSchema fromJson(final JsonObject jsonObject) {
        return new ImmutableObjectSchema(jsonObject);
    }

    static ObjectSchema.Builder newBuilder() {
        return ObjectSchema.Builder.newBuilder();
    }

    static ObjectSchema.Builder newBuilder(final JsonObject jsonObject) {
        return ObjectSchema.Builder.newBuilder(jsonObject);
    }

    default ObjectSchema.Builder toBuilder() {
        return ObjectSchema.Builder.newBuilder(toJson());
    }

    @Override
    default Optional<DataSchemaType> getType() {
        return Optional.of(DataSchemaType.OBJECT);
    }

    Map<String, SingleDataSchema> getProperties();

    List<String> getRequired();

    interface Builder extends SingleDataSchema.Builder<Builder, ObjectSchema> {

        static Builder newBuilder() {
            return new MutableObjectSchemaBuilder(JsonObject.newBuilder());
        }

        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableObjectSchemaBuilder(jsonObject.toBuilder());
        }

        Builder setProperties(@Nullable Map<String, SingleDataSchema> properties);

        Builder setRequired(@Nullable Collection<String> required);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of an ObjectSchema.
     */
    @Immutable
    final class JsonFields {

        public static final JsonFieldDefinition<JsonObject> PROPERTIES = JsonFactory.newJsonObjectFieldDefinition(
                "properties");

        public static final JsonFieldDefinition<JsonArray> REQUIRED = JsonFactory.newJsonArrayFieldDefinition(
                "required");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
