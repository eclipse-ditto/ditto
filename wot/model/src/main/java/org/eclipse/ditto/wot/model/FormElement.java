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
 * A FormElement is a "hypermedia control that describes how an operation can be performed".
 * <p>
 * Forms provide the protocol binding information for interacting with a Thing's affordances,
 * including the target URI, content type, and security requirements.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#form">WoT TD Form</a>
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#form-serialization-json">WoT TD Form serialization as JSON</a>
 * @param <F> the type of the FormElement.
 * @since 2.4.0
 */
public interface FormElement<F extends FormElement<F>> extends TypedJsonObject<F>, Jsonifiable<JsonObject> {

    /**
     * Returns the target IRI of the form submission, identifying the resource to interact with.
     *
     * @return the target href IRI.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#form">WoT TD Form (href)</a>
     */
    IRI getHref();

    /**
     * Returns the content type (MIME type) of the request/response body (default: "application/json").
     *
     * @return the content type.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#form">WoT TD Form (contentType)</a>
     */
    String getContentType();

    /**
     * Returns the optional content coding indicating the encoding of the payload (e.g., "gzip").
     *
     * @return the optional content coding.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#form">WoT TD Form (contentCoding)</a>
     */
    Optional<String> getContentCoding();

    /**
     * Returns the optional subprotocol identifier for the communication (e.g., "longpoll", "websub", "sse").
     *
     * @return the optional subprotocol.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#form">WoT TD Form (subprotocol)</a>
     */
    Optional<FormElementSubprotocol> getSubprotocol();

    /**
     * Returns the optional security configuration overriding the Thing-level security for this form.
     *
     * @return the optional security configuration.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#form">WoT TD Form (security)</a>
     */
    Optional<Security> getSecurity();

    /**
     * Returns the optional OAuth2 scopes required for this form when using OAuth2 security.
     *
     * @return the optional OAuth2 scopes.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#form">WoT TD Form (scopes)</a>
     */
    Optional<OAuth2Scopes> getScopes();

    /**
     * Returns the optional expected response metadata when the operation succeeds.
     *
     * @return the optional expected response.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#expectedresponse">WoT TD ExpectedResponse</a>
     */
    Optional<FormElementExpectedResponse> getExpectedResponse();

    /**
     * Returns the optional collection of additional expected responses for error cases or alternative outcomes.
     *
     * @return the optional additional responses.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#additionalexpectedresponse">WoT TD AdditionalExpectedResponse</a>
     */
    Optional<FormElementAdditionalResponses> getAdditionalResponses();

    /**
     * A mutable builder for creating {@link FormElement} instances.
     *
     * @param <B> the type of the Builder.
     * @param <E> the type of the FormElement.
     */
    interface Builder<B extends Builder<B, E>, E extends FormElement<E>>
            extends TypedJsonObjectBuilder<B, E> {

        /**
         * Sets the target href IRI.
         *
         * @param href the target IRI, or {@code null} to remove.
         * @return this builder.
         */
        B setHref(@Nullable IRI href);

        /**
         * Sets the content type (MIME type).
         *
         * @param contentType the content type, or {@code null} to remove.
         * @return this builder.
         */
        B setContentType(@Nullable String contentType);

        /**
         * Sets the content coding (e.g., "gzip").
         *
         * @param contentCoding the content coding, or {@code null} to remove.
         * @return this builder.
         */
        B setContentCoding(@Nullable String contentCoding);

        /**
         * Sets the subprotocol identifier.
         *
         * @param subprotocol the subprotocol, or {@code null} to remove.
         * @return this builder.
         */
        B setSubprotocol(@Nullable String subprotocol);

        /**
         * Sets the security configuration.
         *
         * @param security the security configuration, or {@code null} to remove.
         * @return this builder.
         */
        B setSecurity(@Nullable Security security);

        /**
         * Sets the OAuth2 scopes.
         *
         * @param scopes the OAuth2 scopes, or {@code null} to remove.
         * @return this builder.
         */
        B setScopes(@Nullable OAuth2Scopes scopes);

        /**
         * Sets the expected response metadata.
         *
         * @param expectedResponse the expected response, or {@code null} to remove.
         * @return this builder.
         */
        B setExpectedResponse(@Nullable FormElementExpectedResponse expectedResponse);

        /**
         * Sets the additional expected responses.
         *
         * @param additionalResponses the additional responses, or {@code null} to remove.
         * @return this builder.
         */
        B setAdditionalResponses(@Nullable FormElementAdditionalResponses additionalResponses);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a FormElement.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the operation type (single value).
         */
        public static final JsonFieldDefinition<String> OP = JsonFactory.newStringFieldDefinition(
                "op");

        /**
         * JSON field definition for the operation types (multiple values).
         */
        public static final JsonFieldDefinition<JsonArray> OP_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "op");

        /**
         * JSON field definition for the target href IRI.
         */
        public static final JsonFieldDefinition<String> HREF = JsonFactory.newStringFieldDefinition(
                "href");

        /**
         * JSON field definition for the content type.
         */
        public static final JsonFieldDefinition<String> CONTENT_TYPE = JsonFactory.newStringFieldDefinition(
                "contentType");

        /**
         * JSON field definition for the content coding.
         */
        public static final JsonFieldDefinition<String> CONTENT_CODING = JsonFactory.newStringFieldDefinition(
                "contentCoding");

        /**
         * JSON field definition for the subprotocol.
         */
        public static final JsonFieldDefinition<String> SUBPROTOCOL = JsonFactory.newStringFieldDefinition(
                "subprotocol");

        /**
         * JSON field definition for the security configuration (single value).
         */
        public static final JsonFieldDefinition<String> SECURITY = JsonFactory.newStringFieldDefinition(
                "security");

        /**
         * JSON field definition for the security configurations (multiple values).
         */
        public static final JsonFieldDefinition<JsonArray> SECURITY_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "security");

        /**
         * JSON field definition for the OAuth2 scopes (single value).
         */
        public static final JsonFieldDefinition<String> SCOPES = JsonFactory.newStringFieldDefinition(
                "scopes");

        /**
         * JSON field definition for the OAuth2 scopes (multiple values).
         */
        public static final JsonFieldDefinition<JsonArray> SCOPES_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "scopes");

        /**
         * JSON field definition for the expected response.
         */
        public static final JsonFieldDefinition<JsonObject> RESPONSE = JsonFactory.newJsonObjectFieldDefinition(
                "response");

        /**
         * JSON field definition for the additional expected responses.
         */
        public static final JsonFieldDefinition<JsonArray> ADDITIONAL_RESPONSES =
                JsonFactory.newJsonArrayFieldDefinition(
                        "additionalResponses");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
