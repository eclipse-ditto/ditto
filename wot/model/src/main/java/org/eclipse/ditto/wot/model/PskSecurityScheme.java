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
 * A PskSecurityScheme is a {@link SecurityScheme} indicating to use a pre-shared key for authentication.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#psksecurityscheme">WoT TD PSKSecurityScheme</a>
 * @since 2.4.0
 */
public interface PskSecurityScheme extends SecurityScheme {

    /**
     * Creates a new PskSecurityScheme from the specified JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object representing the security scheme.
     * @return the PskSecurityScheme.
     */
    static PskSecurityScheme fromJson(final String securitySchemeName, final JsonObject jsonObject) {
        return new ImmutablePskSecurityScheme(securitySchemeName, jsonObject);
    }

    /**
     * Creates a new builder for building a PskSecurityScheme.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     * @throws NullPointerException if {@code securitySchemeName} is {@code null}.
     */
    static PskSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
        return PskSecurityScheme.Builder.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building a PskSecurityScheme, initialized with the values from the specified
     * JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if {@code securitySchemeName} is {@code null}.
     */
    static PskSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
        return PskSecurityScheme.Builder.newBuilder(securitySchemeName, jsonObject);
    }

    @Override
    default SecuritySchemeScheme getScheme() {
        return SecuritySchemeScheme.PSK;
    }

    /**
     * Returns the optional identity hint for the PSK.
     *
     * @return the optional identity.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#psksecurityscheme">WoT TD PSKSecurityScheme (identity)</a>
     */
    Optional<String> getIdentity();


    /**
     * A mutable builder with a fluent API for building a {@link PskSecurityScheme}.
     */
    interface Builder extends SecurityScheme.Builder<Builder, PskSecurityScheme> {

        /**
         * Creates a new builder for building a PskSecurityScheme.
         *
         * @param securitySchemeName the name of the security scheme.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence securitySchemeName) {
            return new MutablePskSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building a PskSecurityScheme, initialized with the values from the
         * specified JSON object.
         *
         * @param securitySchemeName the name of the security scheme.
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
            return new MutablePskSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    jsonObject.toBuilder());
        }

        /**
         * Sets the identity hint for the PSK.
         *
         * @param identity the identity, or {@code null} to remove.
         * @return this builder.
         */
        Builder setIdentity(@Nullable String identity);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a PskSecurityScheme.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the PSK identity.
         */
        public static final JsonFieldDefinition<String> IDENTITY = JsonFactory.newStringFieldDefinition(
                "identity");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
