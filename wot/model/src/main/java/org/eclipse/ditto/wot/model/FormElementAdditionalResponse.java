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

    static FormElementAdditionalResponse fromJson(final JsonObject jsonObject) {
        return new ImmutableFormElementAdditionalResponse(jsonObject);
    }

    static FormElementAdditionalResponse.Builder newBuilder() {
        return FormElementAdditionalResponse.Builder.newBuilder();
    }

    static FormElementAdditionalResponse.Builder newBuilder(final JsonObject jsonObject) {
        return FormElementAdditionalResponse.Builder.newBuilder(jsonObject);
    }

    default FormElementAdditionalResponse.Builder toBuilder() {
        return FormElementAdditionalResponse.Builder.newBuilder(toJson());
    }

    boolean isSuccess();

    Optional<String> getContentType();

    Optional<String> getSchema();

    interface Builder {

        static Builder newBuilder() {
            return new MutableFormElementAdditionalResponseBuilder(JsonObject.newBuilder());
        }

        static Builder newBuilder(final JsonObject jsonObject) {
            return new MutableFormElementAdditionalResponseBuilder(jsonObject.toBuilder());
        }

        Builder setSuccess(@Nullable Boolean success);

        Builder setContentType(@Nullable String contentType);

        Builder setSchema(@Nullable String schema);

        FormElementAdditionalResponse build();
    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a FormElementAdditionalResponse.
     */
    @Immutable
    final class JsonFields {

        public static final JsonFieldDefinition<Boolean> SUCCESS = JsonFactory.newBooleanFieldDefinition(
                "success");

        public static final JsonFieldDefinition<String> CONTENT_TYPE = JsonFactory.newStringFieldDefinition(
                "contentType");

        public static final JsonFieldDefinition<String> SCHEMA = JsonFactory.newStringFieldDefinition(
                "schema");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
