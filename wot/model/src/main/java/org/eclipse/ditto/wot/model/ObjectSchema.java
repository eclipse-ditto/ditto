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
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#objectschema">WoT TD ObjectSchema</a>
 * @since 2.4.0
 */
public interface ObjectSchema extends SingleDataSchema {

    /**
     * Creates a new ObjectSchema from the specified JSON object.
     *
     * @param jsonObject the JSON object representing the object schema.
     * @return the ObjectSchema.
     */
    static ObjectSchema fromJson(final JsonObject jsonObject) {
        return new ImmutableObjectSchema(jsonObject);
    }

    /**
     * Creates a new builder for building an ObjectSchema.
     *
     * @return the builder.
     */
    static ObjectSchema.Builder newBuilder() {
        return ObjectSchema.Builder.newBuilder();
    }

    /**
     * Creates a new builder for building an ObjectSchema, initialized with the values from the specified
     * JSON object.
     *
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     */
    static ObjectSchema.Builder newBuilder(final JsonObject jsonObject) {
        return ObjectSchema.Builder.newBuilder(jsonObject);
    }

    /**
     * Returns a mutable builder with a fluent API for building an ObjectSchema, initialized with the values
     * of this instance.
     *
     * @return the builder.
     */
    default ObjectSchema.Builder toBuilder() {
        return ObjectSchema.Builder.newBuilder(toJson());
    }

    @Override
    default Optional<DataSchemaType> getType() {
        return Optional.of(DataSchemaType.OBJECT);
    }

    /**
     * Returns the map of property names to their data schemas defining the object's properties.
     *
     * @return the map of properties (may be empty).
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#objectschema">WoT TD ObjectSchema (properties)</a>
     */
    Map<String, SingleDataSchema> getProperties();

    /**
     * Returns the list of property names that are required to be present in the object.
     *
     * @return the list of required property names (may be empty).
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#objectschema">WoT TD ObjectSchema (required)</a>
     */
    List<String> getRequired();

    /**
     * A mutable builder with a fluent API for building an {@link ObjectSchema}.
     */
    interface Builder extends SingleDataSchema.Builder<Builder, ObjectSchema> {

        /**
         * Creates a new builder for building an ObjectSchema.
         *
         * @return the builder.
         */
        static Builder newBuilder() {
            return new MutableObjectSchemaBuilder(JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building an ObjectSchema, initialized with the values from the specified
         * JSON object.
         *
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableObjectSchemaBuilder(jsonObject.toBuilder());
        }

        /**
         * Sets the map of property schemas.
         *
         * @param properties the map of property names to schemas, or {@code null} to remove.
         * @return this builder.
         */
        Builder setProperties(@Nullable Map<String, SingleDataSchema> properties);

        /**
         * Sets the list of required property names.
         *
         * @param required the collection of required property names, or {@code null} to remove.
         * @return this builder.
         */
        Builder setRequired(@Nullable Collection<String> required);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of an ObjectSchema.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the object properties map.
         */
        public static final JsonFieldDefinition<JsonObject> PROPERTIES = JsonFactory.newJsonObjectFieldDefinition(
                "properties");

        /**
         * JSON field definition for the required property names array.
         */
        public static final JsonFieldDefinition<JsonArray> REQUIRED = JsonFactory.newJsonArrayFieldDefinition(
                "required");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
