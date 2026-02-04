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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * An OAuth2SecurityScheme is a {@link SecurityScheme} indicating to use {@code OAuth 2.0} for authentication.
 * <p>
 * This security scheme supports the OAuth 2.0 authorization framework, enabling secure delegated access
 * using access tokens.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bib-rfc6749">RFC6749 - The OAuth 2.0 Authorization Framework</a>
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bib-rfc8252">RFC8252 - OAuth 2.0 for native Apps</a>
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bib-rfc8628">RFC8628 - OAuth 2.0 Device Authorization Grant</a>
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#oauth2securityscheme">WoT TD OAuth2SecurityScheme</a>
 * @since 2.4.0
 */
public interface OAuth2SecurityScheme extends SecurityScheme {

    /**
     * Creates a new OAuth2SecurityScheme from the specified JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object representing the security scheme.
     * @return the OAuth2SecurityScheme.
     */
    static OAuth2SecurityScheme fromJson(final String securitySchemeName, final JsonObject jsonObject) {
        return new ImmutableOAuth2SecurityScheme(securitySchemeName, jsonObject);
    }

    /**
     * Creates a new builder for building an OAuth2SecurityScheme.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     * @throws NullPointerException if {@code securitySchemeName} is {@code null}.
     */
    static OAuth2SecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
        return OAuth2SecurityScheme.Builder.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building an OAuth2SecurityScheme, initialized with the values from the specified
     * JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if {@code securitySchemeName} is {@code null}.
     */
    static OAuth2SecurityScheme.Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
        return OAuth2SecurityScheme.Builder.newBuilder(securitySchemeName, jsonObject);
    }

    @Override
    default SecuritySchemeScheme getScheme() {
        return SecuritySchemeScheme.OAUTH2;
    }

    /**
     * Returns the optional URI of the authorization server for user authorization.
     *
     * @return the optional authorization endpoint URI.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#oauth2securityscheme">WoT TD OAuth2SecurityScheme (authorization)</a>
     */
    Optional<IRI> getAuthorization();

    /**
     * Returns the optional URI of the token server for obtaining access tokens.
     *
     * @return the optional token endpoint URI.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#oauth2securityscheme">WoT TD OAuth2SecurityScheme (token)</a>
     */
    Optional<IRI> getToken();

    /**
     * Returns the optional URI of the server for refreshing access tokens.
     *
     * @return the optional refresh endpoint URI.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#oauth2securityscheme">WoT TD OAuth2SecurityScheme (refresh)</a>
     */
    Optional<IRI> getRefresh();

    /**
     * Returns the optional OAuth2 scopes required for authorization.
     * <p>
     * Scopes define the extent of access requested by the client.
     * </p>
     *
     * @return the optional OAuth2 scopes.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#oauth2securityscheme">WoT TD OAuth2SecurityScheme (scopes)</a>
     */
    Optional<OAuth2Scopes> getScopes();

    /**
     * Returns the optional OAuth2 flow type being used.
     * <p>
     * Common flow types include "code" (Authorization Code), "client" (Client Credentials),
     * "device" (Device Authorization), and "implicit" (Implicit Grant - deprecated).
     * </p>
     *
     * @return the optional OAuth2 flow type.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#oauth2securityscheme">WoT TD OAuth2SecurityScheme (flow)</a>
     */
    Optional<OAuth2Flow> getFlow();


    /**
     * A mutable builder with a fluent API for building an {@link OAuth2SecurityScheme}.
     */
    interface Builder extends SecurityScheme.Builder<Builder, OAuth2SecurityScheme> {

        /**
         * Creates a new builder for building an OAuth2SecurityScheme.
         *
         * @param securitySchemeName the name of the security scheme.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence securitySchemeName) {
            return new MutableOAuth2SecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building an OAuth2SecurityScheme, initialized with the values from the specified
         * JSON object.
         *
         * @param securitySchemeName the name of the security scheme.
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
            return new MutableOAuth2SecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    jsonObject.toBuilder());
        }

        /**
         * Sets the authorization endpoint URI.
         *
         * @param authorization the authorization URI, or {@code null} to remove.
         * @return this builder.
         */
        Builder setAuthorization(@Nullable IRI authorization);

        /**
         * Sets the token endpoint URI.
         *
         * @param token the token URI, or {@code null} to remove.
         * @return this builder.
         */
        Builder setToken(@Nullable IRI token);

        /**
         * Sets the refresh endpoint URI.
         *
         * @param refresh the refresh URI, or {@code null} to remove.
         * @return this builder.
         */
        Builder setRefresh(@Nullable IRI refresh);

        /**
         * Sets the OAuth2 scopes.
         *
         * @param scopes the OAuth2 scopes, or {@code null} to remove.
         * @return this builder.
         */
        Builder setScopes(@Nullable OAuth2Scopes scopes);

        /**
         * Sets the OAuth2 flow type.
         *
         * @param flow the flow type (e.g., "code", "client", "device"), or {@code null} to remove.
         * @return this builder.
         */
        Builder setFlow(@Nullable String flow);

    }
    
    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of an OAuth2SecurityScheme.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the authorization endpoint URI.
         */
        public static final JsonFieldDefinition<String> AUTHORIZATION = JsonFactory.newStringFieldDefinition(
                "authorization");

        /**
         * JSON field definition for the token endpoint URI.
         */
        public static final JsonFieldDefinition<String> TOKEN = JsonFactory.newStringFieldDefinition(
                "token");

        /**
         * JSON field definition for the refresh endpoint URI.
         */
        public static final JsonFieldDefinition<String> REFRESH = JsonFactory.newStringFieldDefinition(
                "refresh");

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
         * JSON field definition for the OAuth2 flow type.
         */
        public static final JsonFieldDefinition<String> FLOW = JsonFactory.newStringFieldDefinition(
                "flow");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
