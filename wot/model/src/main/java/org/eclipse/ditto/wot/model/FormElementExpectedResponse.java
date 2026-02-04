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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * A FormElementExpectedResponse describes the expected response message for the primary response.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#expectedresponse">WoT TD ExpectedResponse</a>
 * @since 2.4.0
 */
public interface FormElementExpectedResponse extends TypedJsonObject<FormElementExpectedResponse>, Jsonifiable<JsonObject> {

    /**
     * Creates a new FormElementExpectedResponse from the specified JSON object.
     *
     * @param jsonObject the JSON object representing the expected response.
     * @return the FormElementExpectedResponse.
     */
    static FormElementExpectedResponse fromJson(final JsonObject jsonObject) {
        return new ImmutableFormElementExpectedResponse(jsonObject);
    }

    /**
     * Returns the optional content type of the expected response.
     *
     * @return the optional content type.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#expectedresponse">WoT TD ExpectedResponse (contentType)</a>
     */
    Optional<String> getContentType();

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a FormElementExpectedResponse.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the content type.
         */
        public static final JsonFieldDefinition<String> CONTENT_TYPE = JsonFactory.newStringFieldDefinition(
                "contentType");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
