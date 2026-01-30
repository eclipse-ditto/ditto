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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * An ApiKeySecurityScheme is a {@link SecurityScheme} indicating to use an API key / API token, "for example when a key
 * in an unknown or proprietary format is provided by a cloud service provider."
 * <p>
 * API key authentication is commonly used for simple service-to-service authentication.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#apikeysecurityscheme">WoT TD APIKeySecurityScheme</a>
 * @since 2.4.0
 */
public interface ApiKeySecurityScheme extends SecurityScheme {

    /**
     * Creates a new ApiKeySecurityScheme from the specified JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object representing the security scheme.
     * @return the ApiKeySecurityScheme.
     */
    static ApiKeySecurityScheme fromJson(final String securitySchemeName, final JsonObject jsonObject) {
        return new ImmutableApiKeySecurityScheme(securitySchemeName, jsonObject);
    }

    /**
     * Creates a new builder for building an ApiKeySecurityScheme.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     * @throws NullPointerException if {@code securitySchemeName} is {@code null}.
     */
    static ApiKeySecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
        return ApiKeySecurityScheme.Builder.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building an ApiKeySecurityScheme, initialized with the values from the specified
     * JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if {@code securitySchemeName} is {@code null}.
     */
    static ApiKeySecurityScheme.Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
        return ApiKeySecurityScheme.Builder.newBuilder(securitySchemeName, jsonObject);
    }

    @Override
    default SecuritySchemeScheme getScheme() {
        return SecuritySchemeScheme.APIKEY;
    }

    /**
     * Returns the optional location where the API key should be provided.
     * <p>
     * Possible values are "header", "query", "body", or "cookie". The default is "query".
     * </p>
     *
     * @return the optional location for the API key.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#apikeysecurityscheme">WoT TD APIKeySecurityScheme (in)</a>
     */
    Optional<SecuritySchemeIn> getIn();

    /**
     * Returns the optional name of the header, query parameter, or cookie where the API key should be provided.
     *
     * @return the optional API key parameter name.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#apikeysecurityscheme">WoT TD APIKeySecurityScheme (name)</a>
     */
    Optional<String> getName();


    /**
     * A mutable builder with a fluent API for building an {@link ApiKeySecurityScheme}.
     */
    interface Builder extends SecurityScheme.Builder<Builder, ApiKeySecurityScheme> {

        /**
         * Creates a new builder for building an ApiKeySecurityScheme.
         *
         * @param securitySchemeName the name of the security scheme.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence securitySchemeName) {
            return new MutableApiKeySecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building an ApiKeySecurityScheme, initialized with the values from the specified
         * JSON object.
         *
         * @param securitySchemeName the name of the security scheme.
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
            return new MutableApiKeySecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    jsonObject.toBuilder());
        }

        /**
         * Sets the location where the API key should be provided.
         *
         * @param in the location (e.g., "header", "query", "body", "cookie"), or {@code null} to remove.
         * @return this builder.
         */
        ApiKeySecurityScheme.Builder setIn(@Nullable String in);

        /**
         * Sets the name of the API key parameter.
         *
         * @param name the parameter name, or {@code null} to remove.
         * @return this builder.
         */
        ApiKeySecurityScheme.Builder setName(@Nullable String name);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of an ApiKeySecurityScheme.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the location of the API key.
         */
        public static final JsonFieldDefinition<String> IN = JsonFactory.newStringFieldDefinition(
                "in");

        /**
         * JSON field definition for the API key parameter name.
         */
        public static final JsonFieldDefinition<String> NAME = JsonFactory.newStringFieldDefinition(
                "name");

        private JsonFields() {
            throw new AssertionError();
        }
    }

}
