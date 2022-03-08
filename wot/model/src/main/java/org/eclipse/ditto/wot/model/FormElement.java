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
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * A FormElement is a "hypermedia control that describe how an operation can be performed".
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#form">WoT TD Form</a>
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#form-serialization-json">WoT TD Form serialization as JSON</a>
 * @param <F> the type of the FormElement.
 * @since 2.4.0
 */
public interface FormElement<F extends FormElement<F>> extends TypedJsonObject<F>, Jsonifiable<JsonObject> {

    IRI getHref();

    String getContentType();

    Optional<String> getContentCoding();

    Optional<FormElementSubprotocol> getSubprotocol();

    Optional<Security> getSecurity();

    Optional<OAuth2Scopes> getScopes();

    Optional<FormElementExpectedResponse> getExpectedResponse();

    Optional<FormElementAdditionalResponses> getAdditionalResponses();

    interface Builder<B extends Builder<B, E>, E extends FormElement<E>>
            extends TypedJsonObjectBuilder<B, E> {

        B setHref(@Nullable IRI href);

        B setContentType(@Nullable String contentType);

        B setContentCoding(@Nullable String contentCoding);

        B setSubprotocol(@Nullable String subprotocol);

        B setSecurity(@Nullable Security security);

        B setScopes(@Nullable OAuth2Scopes scopes);

        B setExpectedResponse(@Nullable FormElementExpectedResponse expectedResponse);

        B setAdditionalResponses(@Nullable FormElementAdditionalResponses additionalResponses);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a FormElement.
     */
    @Immutable
    final class JsonFields {

        public static final JsonFieldDefinition<String> OP = JsonFactory.newStringFieldDefinition(
                "op");

        public static final JsonFieldDefinition<JsonArray> OP_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "op");

        public static final JsonFieldDefinition<String> HREF = JsonFactory.newStringFieldDefinition(
                "href");

        public static final JsonFieldDefinition<String> CONTENT_TYPE = JsonFactory.newStringFieldDefinition(
                "contentType");

        public static final JsonFieldDefinition<String> CONTENT_CODING = JsonFactory.newStringFieldDefinition(
                "contentCoding");

        public static final JsonFieldDefinition<String> SUBPROTOCOL = JsonFactory.newStringFieldDefinition(
                "subprotocol");

        public static final JsonFieldDefinition<String> SECURITY = JsonFactory.newStringFieldDefinition(
                "security");

        public static final JsonFieldDefinition<JsonArray> SECURITY_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "security");

        public static final JsonFieldDefinition<String> SCOPES = JsonFactory.newStringFieldDefinition(
                "scopes");

        public static final JsonFieldDefinition<JsonArray> SCOPES_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "scopes");

        public static final JsonFieldDefinition<JsonObject> RESPONSE = JsonFactory.newJsonObjectFieldDefinition(
                "response");

        public static final JsonFieldDefinition<JsonArray> ADDITIONAL_RESPONSES =
                JsonFactory.newJsonArrayFieldDefinition(
                        "additionalResponses");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
