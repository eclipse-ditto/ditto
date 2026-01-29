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

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * An AllOfComboSecurityScheme is a {@link ComboSecurityScheme} in which "all of" the specified linked
 * {@link SecurityScheme}s must apply.
 *
 * @since 2.4.0
 */
public interface AllOfComboSecurityScheme extends ComboSecurityScheme {

    /**
     * Creates a new AllOfComboSecurityScheme from the specified JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object representing the security scheme.
     * @return the AllOfComboSecurityScheme.
     */
    static AllOfComboSecurityScheme fromJson(final String securitySchemeName, final JsonObject jsonObject) {
        return new ImmutableAllOfComboSecurityScheme(securitySchemeName, jsonObject);
    }

    /**
     * Creates a new builder for building an AllOfComboSecurityScheme.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     * @throws NullPointerException if {@code securitySchemeName} is {@code null}.
     */
    static AllOfComboSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
        return AllOfComboSecurityScheme.Builder.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building an AllOfComboSecurityScheme, initialized with the values from the specified
     * JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if {@code securitySchemeName} is {@code null}.
     */
    static AllOfComboSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName,
            final JsonObject jsonObject) {
        return AllOfComboSecurityScheme.Builder.newBuilder(securitySchemeName, jsonObject);
    }

    @Override
    default SecuritySchemeScheme getScheme() {
        return SecuritySchemeScheme.COMBO;
    }

    /**
     * Returns the list of security scheme names that must all be applied.
     *
     * @return the list of security scheme names.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#combosecurityscheme">WoT TD ComboSecurityScheme (allOf)</a>
     */
    List<String> getAllOf();


    /**
     * A mutable builder with a fluent API for building an {@link AllOfComboSecurityScheme}.
     */
    interface Builder extends SecurityScheme.Builder<Builder, AllOfComboSecurityScheme> {

        /**
         * Creates a new builder for building an AllOfComboSecurityScheme.
         *
         * @param securitySchemeName the name of the security scheme.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence securitySchemeName) {
            return new MutableAllOfComboSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building an AllOfComboSecurityScheme, initialized with the values from the
         * specified JSON object.
         *
         * @param securitySchemeName the name of the security scheme.
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence securitySchemeName,
                final JsonObject jsonObject) {
            return new MutableAllOfComboSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    jsonObject.toBuilder());
        }

        /**
         * Sets the security schemes that must all be applied.
         *
         * @param securitySchemes the security schemes, or {@code null} to remove.
         * @return this builder.
         */
        Builder setAllOf(@Nullable Collection<SecurityScheme> securitySchemes);

    }

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonFieldDefinition}s of an AllOfComboSecurityScheme.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the array of security scheme names that must all apply.
         */
        public static final JsonFieldDefinition<JsonArray> ALL_OF = JsonFactory.newJsonArrayFieldDefinition(
                "allOf");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
