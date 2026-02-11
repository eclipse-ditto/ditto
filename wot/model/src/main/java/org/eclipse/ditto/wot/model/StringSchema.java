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
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * A StringSchema is a {@link SingleDataSchema} describing the JSON data type {@code string}.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#stringschema">WoT TD StringSchema</a>
 * @since 2.4.0
 */
public interface StringSchema extends SingleDataSchema {

    /**
     * Creates a new StringSchema from the specified JSON object.
     *
     * @param jsonObject the JSON object representing the string schema.
     * @return the StringSchema.
     */
    static StringSchema fromJson(final JsonObject jsonObject) {
        return new ImmutableStringSchema(jsonObject);
    }

    /**
     * Creates a new builder for building a StringSchema.
     *
     * @return the builder.
     */
    static StringSchema.Builder newBuilder() {
        return StringSchema.Builder.newBuilder();
    }

    /**
     * Creates a new builder for building a StringSchema, initialized with the values from the specified
     * JSON object.
     *
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     */
    static StringSchema.Builder newBuilder(final JsonObject jsonObject) {
        return StringSchema.Builder.newBuilder(jsonObject);
    }

    /**
     * Returns a mutable builder with a fluent API for building a StringSchema, initialized with the values
     * of this instance.
     *
     * @return the builder.
     */
    default StringSchema.Builder toBuilder() {
        return StringSchema.Builder.newBuilder(toJson());
    }

    @Override
    default Optional<DataSchemaType> getType() {
        return Optional.of(DataSchemaType.STRING);
    }

    /**
     * Returns the optional minimum length (in characters) that the string must have.
     *
     * @return the optional minimum length.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#stringschema">WoT TD StringSchema (minLength)</a>
     */
    Optional<Integer> getMinLength();

    /**
     * Returns the optional maximum length (in characters) that the string may have.
     *
     * @return the optional maximum length.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#stringschema">WoT TD StringSchema (maxLength)</a>
     */
    Optional<Integer> getMaxLength();

    /**
     * Returns the optional regular expression pattern that the string must match.
     *
     * @return the optional regex pattern.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#stringschema">WoT TD StringSchema (pattern)</a>
     */
    Optional<Pattern> getPattern();

    /**
     * Returns the optional content encoding for binary data (e.g., "base64", "base16").
     *
     * @return the optional content encoding.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#stringschema">WoT TD StringSchema (contentEncoding)</a>
     */
    Optional<String> getContentEncoding();

    /**
     * Returns the optional MIME type of the content encoded in the string.
     *
     * @return the optional content media type.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#stringschema">WoT TD StringSchema (contentMediaType)</a>
     */
    Optional<String> getContentMediaType();

    /**
     * A mutable builder with a fluent API for building a {@link StringSchema}.
     */
    interface Builder extends SingleDataSchema.Builder<Builder, StringSchema> {

        /**
         * Creates a new builder for building a StringSchema.
         *
         * @return the builder.
         */
        static Builder newBuilder() {
            return new MutableStringSchemaBuilder(JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building a StringSchema, initialized with the values from the specified
         * JSON object.
         *
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableStringSchemaBuilder(jsonObject.toBuilder());
        }

        /**
         * Sets the minimum length constraint.
         *
         * @param minLength the minimum length, or {@code null} to remove.
         * @return this builder.
         */
        Builder setMinLength(@Nullable Integer minLength);

        /**
         * Sets the maximum length constraint.
         *
         * @param maxLength the maximum length, or {@code null} to remove.
         * @return this builder.
         */
        Builder setMaxLength(@Nullable Integer maxLength);

        /**
         * Sets the regex pattern constraint.
         *
         * @param pattern the pattern, or {@code null} to remove.
         * @return this builder.
         */
        Builder setPattern(@Nullable Pattern pattern);

        /**
         * Sets the content encoding for binary data.
         *
         * @param contentEncoding the content encoding, or {@code null} to remove.
         * @return this builder.
         */
        Builder setContentEncoding(@Nullable String contentEncoding);

        /**
         * Sets the content media type.
         *
         * @param mediaType the media type, or {@code null} to remove.
         * @return this builder.
         */
        Builder setMediaType(@Nullable String mediaType);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a StringSchema.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the minimum length constraint.
         */
        public static final JsonFieldDefinition<Integer> MIN_LENGTH = JsonFactory.newIntFieldDefinition(
                "minLength");

        /**
         * JSON field definition for the maximum length constraint.
         */
        public static final JsonFieldDefinition<Integer> MAX_LENGTH = JsonFactory.newIntFieldDefinition(
                "maxLength");

        /**
         * JSON field definition for the regex pattern constraint.
         */
        public static final JsonFieldDefinition<String> PATTERN = JsonFactory.newStringFieldDefinition(
                "pattern");

        /**
         * JSON field definition for the content encoding.
         */
        public static final JsonFieldDefinition<String> CONTENT_ENCODING = JsonFactory.newStringFieldDefinition(
                "contentEncoding");

        /**
         * JSON field definition for the content media type.
         */
        public static final JsonFieldDefinition<String> CONTENT_MEDIA_TYPE = JsonFactory.newStringFieldDefinition(
                "contentMediaType");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
