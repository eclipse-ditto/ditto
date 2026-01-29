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
 * An OneOfComboSecurityScheme is a {@link ComboSecurityScheme} in which only "one of" the specified linked
 * {@link SecurityScheme}s has to apply.
 *
 * @since 2.4.0
 */
public interface OneOfComboSecurityScheme extends ComboSecurityScheme {

    /**
     * Creates a new OneOfComboSecurityScheme from the specified JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object representing the security scheme.
     * @return the OneOfComboSecurityScheme.
     */
    static OneOfComboSecurityScheme fromJson(final String securitySchemeName, final JsonObject jsonObject) {
        return new ImmutableOneOfComboSecurityScheme(securitySchemeName, jsonObject);
    }

    /**
     * Creates a new builder for building a OneOfComboSecurityScheme.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     * @throws NullPointerException if {@code securitySchemeName} is {@code null}.
     */
    static OneOfComboSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
        return OneOfComboSecurityScheme.Builder.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building a OneOfComboSecurityScheme, initialized with the values from the specified
     * JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if {@code securitySchemeName} is {@code null}.
     */
    static OneOfComboSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName,
            final JsonObject jsonObject) {
        return OneOfComboSecurityScheme.Builder.newBuilder(securitySchemeName, jsonObject);
    }

    @Override
    default SecuritySchemeScheme getScheme() {
        return SecuritySchemeScheme.COMBO;
    }

    /**
     * Returns the list of security scheme names where one of them must be applied.
     *
     * @return the list of security scheme names.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#combosecurityscheme">WoT TD ComboSecurityScheme (oneOf)</a>
     */
    List<String> getOneOf();


    /**
     * A mutable builder with a fluent API for building a {@link OneOfComboSecurityScheme}.
     */
    interface Builder extends SecurityScheme.Builder<Builder, OneOfComboSecurityScheme> {

        /**
         * Creates a new builder for building a OneOfComboSecurityScheme.
         *
         * @param securitySchemeName the name of the security scheme.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence securitySchemeName) {
            return new MutableOneOfComboSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building a OneOfComboSecurityScheme, initialized with the values from the
         * specified JSON object.
         *
         * @param securitySchemeName the name of the security scheme.
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence securitySchemeName,
                final JsonObject jsonObject) {
            return new MutableOneOfComboSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    jsonObject.toBuilder());
        }

        /**
         * Sets the security schemes where one of them must be applied.
         *
         * @param securitySchemes the security schemes, or {@code null} to remove.
         * @return this builder.
         */
        Builder setOneOf(@Nullable Collection<SecurityScheme> securitySchemes);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a OneOfComboSecurityScheme.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the array of security scheme names where one must apply.
         */
        public static final JsonFieldDefinition<JsonArray> ONE_OF = JsonFactory.newJsonArrayFieldDefinition(
                "oneOf");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
