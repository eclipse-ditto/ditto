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
 * A BasicSecurityScheme is a {@link SecurityScheme} indicating to use {@code Basic} Authentication, using an unencrypted
 * username and password.
 * <p>
 * Basic authentication transmits credentials as base64-encoded strings and should only be used over HTTPS.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bib-rfc7617">RFC7617 - Basic Authentication</a>
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#basicsecurityscheme">WoT TD BasicSecurityScheme</a>
 * @since 2.4.0
 */
public interface BasicSecurityScheme extends SecurityScheme {

    /**
     * Creates a new BasicSecurityScheme from the specified JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object representing the security scheme.
     * @return the BasicSecurityScheme.
     */
    static BasicSecurityScheme fromJson(final String securitySchemeName, final JsonObject jsonObject) {
        return new ImmutableBasicSecurityScheme(securitySchemeName, jsonObject);
    }

    /**
     * Creates a new builder for building a BasicSecurityScheme.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     * @throws NullPointerException if {@code securitySchemeName} is {@code null}.
     */
    static BasicSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
        return BasicSecurityScheme.Builder.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building a BasicSecurityScheme, initialized with the values from the specified
     * JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if {@code securitySchemeName} is {@code null}.
     */
    static BasicSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
        return BasicSecurityScheme.Builder.newBuilder(securitySchemeName, jsonObject);
    }

    @Override
    default SecuritySchemeScheme getScheme() {
        return SecuritySchemeScheme.BASIC;
    }

    /**
     * Returns the optional location where the credentials should be provided.
     * <p>
     * Possible values are "header", "query", "body", or "cookie". The default is "header".
     * </p>
     *
     * @return the optional location for credentials.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#basicsecurityscheme">WoT TD BasicSecurityScheme (in)</a>
     */
    Optional<SecuritySchemeIn> getIn();

    /**
     * Returns the optional name of the header, query parameter, or cookie where credentials should be provided.
     *
     * @return the optional credential parameter name.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#basicsecurityscheme">WoT TD BasicSecurityScheme (name)</a>
     */
    Optional<String> getName();


    /**
     * A mutable builder with a fluent API for building a {@link BasicSecurityScheme}.
     */
    interface Builder extends SecurityScheme.Builder<BasicSecurityScheme.Builder, BasicSecurityScheme> {

        /**
         * Creates a new builder for building a BasicSecurityScheme.
         *
         * @param securitySchemeName the name of the security scheme.
         * @return the builder.
         */
        static BasicSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
            return new MutableBasicSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building a BasicSecurityScheme, initialized with the values from the specified
         * JSON object.
         *
         * @param securitySchemeName the name of the security scheme.
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static BasicSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName,
                final JsonObject jsonObject) {
            return new MutableBasicSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    jsonObject.toBuilder());
        }

        /**
         * Sets the location where credentials should be provided.
         *
         * @param in the location (e.g., "header", "query", "body", "cookie"), or {@code null} to remove.
         * @return this builder.
         */
        Builder setIn(@Nullable String in);

        /**
         * Sets the name of the credential parameter.
         *
         * @param name the parameter name, or {@code null} to remove.
         * @return this builder.
         */
        Builder setName(@Nullable String name);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a BasicSecurityScheme.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the location of credentials.
         */
        public static final JsonFieldDefinition<String> IN = JsonFactory.newStringFieldDefinition(
                "in");

        /**
         * JSON field definition for the credential parameter name.
         */
        public static final JsonFieldDefinition<String> NAME = JsonFactory.newStringFieldDefinition(
                "name");

        private JsonFields() {
            throw new AssertionError();
        }
    }

}
