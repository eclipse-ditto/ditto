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
 * @since 2.4.0
 */
public interface StringSchema extends SingleDataSchema {

    static StringSchema fromJson(final JsonObject jsonObject) {
        return new ImmutableStringSchema(jsonObject);
    }

    static StringSchema.Builder newBuilder() {
        return StringSchema.Builder.newBuilder();
    }

    static StringSchema.Builder newBuilder(final JsonObject jsonObject) {
        return StringSchema.Builder.newBuilder(jsonObject);
    }

    default StringSchema.Builder toBuilder() {
        return StringSchema.Builder.newBuilder(toJson());
    }

    @Override
    default Optional<DataSchemaType> getType() {
        return Optional.of(DataSchemaType.STRING);
    }

    Optional<Integer> getMinLength();

    Optional<Integer> getMaxLength();

    Optional<Pattern> getPattern();

    Optional<String> getContentEncoding();

    Optional<String> getContentMediaType();

    interface Builder extends SingleDataSchema.Builder<Builder, StringSchema> {

        static Builder newBuilder() {
            return new MutableStringSchemaBuilder(JsonObject.newBuilder());
        }

        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableStringSchemaBuilder(jsonObject.toBuilder());
        }

        Builder setMinLength(@Nullable Integer minLength);

        Builder setMaxLength(@Nullable Integer maxLength);

        Builder setPattern(@Nullable Pattern pattern);

        Builder setContentEncoding(@Nullable String contentEncoding);

        Builder setMediaType(@Nullable String mediaType);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a StringSchema.
     */
    @Immutable
    final class JsonFields {

        public static final JsonFieldDefinition<Integer> MIN_LENGTH = JsonFactory.newIntFieldDefinition(
                "minLength");

        public static final JsonFieldDefinition<Integer> MAX_LENGTH = JsonFactory.newIntFieldDefinition(
                "maxLength");

        public static final JsonFieldDefinition<String> PATTERN = JsonFactory.newStringFieldDefinition(
                "pattern");

        public static final JsonFieldDefinition<String> CONTENT_ENCODING = JsonFactory.newStringFieldDefinition(
                "contentEncoding");

        public static final JsonFieldDefinition<String> CONTENT_MEDIA_TYPE = JsonFactory.newStringFieldDefinition(
                "contentMediaType");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
