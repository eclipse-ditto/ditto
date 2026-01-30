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

import java.util.Arrays;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * A DigestSecurityScheme is a {@link SecurityScheme} indicating to use {@code Digest Access} Authentication.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bib-rfc7616">RFC7617 - Digest Access Authentication</a>
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#digestsecurityscheme">WoT TD DigestSecurityScheme</a>
 * @since 2.4.0
 */
public interface DigestSecurityScheme extends SecurityScheme {

    /**
     * Creates a new DigestSecurityScheme from the specified JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object representing the security scheme.
     * @return the DigestSecurityScheme.
     */
    static DigestSecurityScheme fromJson(final String securitySchemeName, final JsonObject jsonObject) {
        return new ImmutableDigestSecurityScheme(securitySchemeName, jsonObject);
    }

    /**
     * Creates a new builder for building a DigestSecurityScheme.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     * @throws NullPointerException if {@code securitySchemeName} is {@code null}.
     */
    static DigestSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
        return DigestSecurityScheme.Builder.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building a DigestSecurityScheme, initialized with the values from the specified
     * JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if {@code securitySchemeName} is {@code null}.
     */
    static DigestSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
        return DigestSecurityScheme.Builder.newBuilder(securitySchemeName, jsonObject);
    }

    @Override
    default SecuritySchemeScheme getScheme() {
        return SecuritySchemeScheme.DIGEST;
    }

    /**
     * Returns the optional quality of protection (qop) parameter for digest authentication.
     *
     * @return the optional qop value.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#digestsecurityscheme">WoT TD DigestSecurityScheme (qop)</a>
     */
    Optional<Qop> getQop();

    /**
     * Returns the optional location where the credentials should be provided.
     * <p>
     * Possible values are "header", "query", "body", or "cookie". The default is "header".
     * </p>
     *
     * @return the optional location for credentials.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#digestsecurityscheme">WoT TD DigestSecurityScheme (in)</a>
     */
    Optional<SecuritySchemeIn> getIn();

    /**
     * Returns the optional name of the header, query parameter, or cookie where credentials should be provided.
     *
     * @return the optional credential parameter name.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#digestsecurityscheme">WoT TD DigestSecurityScheme (name)</a>
     */
    Optional<String> getName();


    /**
     * A mutable builder with a fluent API for building a {@link DigestSecurityScheme}.
     */
    interface Builder extends SecurityScheme.Builder<Builder, DigestSecurityScheme> {

        /**
         * Creates a new builder for building a DigestSecurityScheme.
         *
         * @param securitySchemeName the name of the security scheme.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence securitySchemeName) {
            return new MutableDigestSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building a DigestSecurityScheme, initialized with the values from the
         * specified JSON object.
         *
         * @param securitySchemeName the name of the security scheme.
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence securitySchemeName,
                final JsonObject jsonObject) {
            return new MutableDigestSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    jsonObject.toBuilder());
        }

        /**
         * Sets the quality of protection (qop) parameter.
         *
         * @param qop the qop value, or {@code null} to remove.
         * @return this builder.
         */
        Builder setQop(@Nullable Qop qop);

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
     * Enumeration of quality of protection (qop) values for digest authentication.
     *
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#digestsecurityscheme">WoT TD DigestSecurityScheme (qop)</a>
     */
    enum Qop implements CharSequence {
        /**
         * Authentication only.
         */
        AUTH("auth"),
        /**
         * Authentication with integrity protection.
         */
        AUTH_INT("auth-int");

        private final String name;

        Qop(final String name) {
            this.name = name;
        }

        /**
         * Returns the string representation of this qop value.
         *
         * @return the qop name.
         */
        public String getName() {
            return name;
        }

        /**
         * Returns the {@code Qop} for the given {@code name} if it exists.
         *
         * @param name the name.
         * @return the Qop or an empty optional.
         */
        public static Optional<Qop> forName(final CharSequence name) {
            checkNotNull(name, "name");
            return Arrays.stream(values())
                    .filter(c -> c.name.contentEquals(name))
                    .findFirst();
        }

        @Override
        public int length() {
            return name.length();
        }

        @Override
        public char charAt(final int index) {
            return name.charAt(index);
        }

        @Override
        public CharSequence subSequence(final int start, final int end) {
            return name.subSequence(start, end);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a DigestSecurityScheme.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the quality of protection (qop).
         */
        public static final JsonFieldDefinition<String> QOP = JsonFactory.newStringFieldDefinition(
                "qop");

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
