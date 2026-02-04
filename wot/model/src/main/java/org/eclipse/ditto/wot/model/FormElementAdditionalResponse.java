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

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * A FormElementAdditionalResponse describes the expected additional response messages for {@link FormElement}s, e.g.
 * error responses.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#additionalexpectedresponse">WoT TD AdditionalExpectedResponse</a>
 * @since 2.4.0
 */
public interface FormElementAdditionalResponse extends Jsonifiable<JsonObject> {

    /**
     * Creates a new FormElementAdditionalResponse from the specified JSON object.
     *
     * @param jsonObject the JSON object representing the additional response.
     * @return the FormElementAdditionalResponse.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    static FormElementAdditionalResponse fromJson(final JsonObject jsonObject) {
        return new ImmutableFormElementAdditionalResponse(jsonObject);
    }

    /**
     * Creates a new builder for building a FormElementAdditionalResponse.
     *
     * @return the builder.
     */
    static FormElementAdditionalResponse.Builder newBuilder() {
        return FormElementAdditionalResponse.Builder.newBuilder();
    }

    /**
     * Creates a new builder for building a FormElementAdditionalResponse, initialized with the values from the
     * specified JSON object.
     *
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    static FormElementAdditionalResponse.Builder newBuilder(final JsonObject jsonObject) {
        return FormElementAdditionalResponse.Builder.newBuilder(jsonObject);
    }

    /**
     * Returns a mutable builder with a fluent API for building a FormElementAdditionalResponse, initialized with
     * the values of this instance.
     *
     * @return the builder.
     */
    default FormElementAdditionalResponse.Builder toBuilder() {
        return FormElementAdditionalResponse.Builder.newBuilder(toJson());
    }

    /**
     * Returns whether this additional response represents a success case.
     *
     * @return {@code true} if this is a success response, {@code false} otherwise.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#additionalexpectedresponse">WoT TD AdditionalExpectedResponse (success)</a>
     */
    boolean isSuccess();

    /**
     * Returns the optional content type of this additional response.
     *
     * @return the optional content type.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#additionalexpectedresponse">WoT TD AdditionalExpectedResponse (contentType)</a>
     */
    Optional<String> getContentType();

    /**
     * Returns the optional schema reference for this additional response.
     *
     * @return the optional schema.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#additionalexpectedresponse">WoT TD AdditionalExpectedResponse (schema)</a>
     */
    Optional<String> getSchema();

    /**
     * A mutable builder with a fluent API for building a {@link FormElementAdditionalResponse}.
     */
    interface Builder {

        /**
         * Creates a new builder for building a FormElementAdditionalResponse.
         *
         * @return the builder.
         */
        static Builder newBuilder() {
            return new MutableFormElementAdditionalResponseBuilder(JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building a FormElementAdditionalResponse, initialized with the values from
         * the specified JSON object.
         *
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableFormElementAdditionalResponseBuilder(jsonObject.toBuilder());
        }

        /**
         * Sets whether this additional response represents a success case.
         *
         * @param success the success flag, or {@code null} to remove.
         * @return this builder.
         */
        Builder setSuccess(@Nullable Boolean success);

        /**
         * Sets the content type of this additional response.
         *
         * @param contentType the content type, or {@code null} to remove.
         * @return this builder.
         */
        Builder setContentType(@Nullable String contentType);

        /**
         * Sets the schema reference for this additional response.
         *
         * @param schema the schema, or {@code null} to remove.
         * @return this builder.
         */
        Builder setSchema(@Nullable String schema);

        /**
         * Builds the FormElementAdditionalResponse.
         *
         * @return the FormElementAdditionalResponse.
         */
        FormElementAdditionalResponse build();
    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a FormElementAdditionalResponse.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the success flag.
         */
        public static final JsonFieldDefinition<Boolean> SUCCESS = JsonFactory.newBooleanFieldDefinition(
                "success");

        /**
         * JSON field definition for the content type.
         */
        public static final JsonFieldDefinition<String> CONTENT_TYPE = JsonFactory.newStringFieldDefinition(
                "contentType");

        /**
         * JSON field definition for the schema reference.
         */
        public static final JsonFieldDefinition<String> SCHEMA = JsonFactory.newStringFieldDefinition(
                "schema");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
