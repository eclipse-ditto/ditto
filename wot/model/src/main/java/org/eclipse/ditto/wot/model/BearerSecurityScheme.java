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
 * A BearerSecurityScheme is a {@link SecurityScheme} indicating to use {@code Bearer Tokens} "independently of OAuth2".
 * <p>
 * Bearer token authentication is commonly used with JWT tokens or other token formats.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bib-rfc6750">RFC6750 - Bearer Token</a>
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bearersecurityscheme">WoT TD BearerSecurityScheme</a>
 * @since 2.4.0
 */
public interface BearerSecurityScheme extends SecurityScheme {

    /**
     * Creates a new BearerSecurityScheme from the specified JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object representing the security scheme.
     * @return the BearerSecurityScheme.
     */
    static BearerSecurityScheme fromJson(final String securitySchemeName, final JsonObject jsonObject) {
        return new ImmutableBearerSecurityScheme(securitySchemeName, jsonObject);
    }

    /**
     * Creates a new builder for building a BearerSecurityScheme.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     * @throws NullPointerException if {@code securitySchemeName} is {@code null}.
     */
    static BearerSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
        return BearerSecurityScheme.Builder.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building a BearerSecurityScheme, initialized with the values from the specified
     * JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if {@code securitySchemeName} is {@code null}.
     */
    static BearerSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
        return BearerSecurityScheme.Builder.newBuilder(securitySchemeName, jsonObject);
    }

    @Override
    default SecuritySchemeScheme getScheme() {
        return SecuritySchemeScheme.BEARER;
    }

    /**
     * Returns the optional URI of the authorization server for obtaining tokens.
     *
     * @return the optional authorization URI.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bearersecurityscheme">WoT TD BearerSecurityScheme (authorization)</a>
     */
    Optional<IRI> getAuthorization();

    /**
     * Returns the optional encoding algorithm used for signing the token (e.g., "ES256", "ES512-256").
     *
     * @return the optional algorithm identifier.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bearersecurityscheme">WoT TD BearerSecurityScheme (alg)</a>
     */
    Optional<String> getAlg();

    /**
     * Returns the optional token format (e.g., "jwt", "cwt", "jwe").
     *
     * @return the optional format identifier.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bearersecurityscheme">WoT TD BearerSecurityScheme (format)</a>
     */
    Optional<String> getFormat();

    /**
     * Returns the optional location where the token should be provided.
     * <p>
     * Possible values are "header", "query", "body", or "cookie". The default is "header".
     * </p>
     *
     * @return the optional location for the token.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bearersecurityscheme">WoT TD BearerSecurityScheme (in)</a>
     */
    Optional<SecuritySchemeIn> getIn();

    /**
     * Returns the optional name of the header, query parameter, or cookie where the token should be provided.
     *
     * @return the optional token parameter name.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bearersecurityscheme">WoT TD BearerSecurityScheme (name)</a>
     */
    Optional<String> getName();


    /**
     * A mutable builder with a fluent API for building a {@link BearerSecurityScheme}.
     */
    interface Builder extends SecurityScheme.Builder<Builder, BearerSecurityScheme> {

        /**
         * Creates a new builder for building a BearerSecurityScheme.
         *
         * @param securitySchemeName the name of the security scheme.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence securitySchemeName) {
            return new MutableBearerSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building a BearerSecurityScheme, initialized with the values from the specified
         * JSON object.
         *
         * @param securitySchemeName the name of the security scheme.
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
            return new MutableBearerSecuritySchemeBuilder(
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
         * Sets the signing algorithm identifier.
         *
         * @param alg the algorithm, or {@code null} to remove.
         * @return this builder.
         */
        Builder setAlg(@Nullable String alg);

        /**
         * Sets the token format.
         *
         * @param format the format, or {@code null} to remove.
         * @return this builder.
         */
        Builder setFormat(@Nullable String format);

        /**
         * Sets the location where the token should be provided.
         *
         * @param in the location (e.g., "header", "query", "body", "cookie"), or {@code null} to remove.
         * @return this builder.
         */
        Builder setIn(@Nullable String in);

        /**
         * Sets the name of the token parameter.
         *
         * @param name the parameter name, or {@code null} to remove.
         * @return this builder.
         */
        Builder setName(@Nullable String name);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a BearerSecurityScheme.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the authorization endpoint URI.
         */
        public static final JsonFieldDefinition<String> AUTHORIZATION = JsonFactory.newStringFieldDefinition(
                "authorization");

        /**
         * JSON field definition for the signing algorithm.
         */
        public static final JsonFieldDefinition<String> ALG = JsonFactory.newStringFieldDefinition(
                "alg");

        /**
         * JSON field definition for the token format.
         */
        public static final JsonFieldDefinition<String> FORMAT = JsonFactory.newStringFieldDefinition(
                "format");

        /**
         * JSON field definition for the location of the token.
         */
        public static final JsonFieldDefinition<String> IN = JsonFactory.newStringFieldDefinition(
                "in");

        /**
         * JSON field definition for the token parameter name.
         */
        public static final JsonFieldDefinition<String> NAME = JsonFactory.newStringFieldDefinition(
                "name");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
