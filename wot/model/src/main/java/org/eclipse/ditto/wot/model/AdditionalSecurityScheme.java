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

import org.eclipse.ditto.json.JsonObject;

/**
 * An AdditionalSecurityScheme is a {@link SecurityScheme} which can be defined via {@code TD Context Extension}
 * mechanism for new security schemes not included in the "Security Vocabulary Definitions".
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#dfn-context-ext">WoT TD Context Extension</a>
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#adding-security-schemes">WoT TD Adding Security Schemes</a>
 * @since 3.0.0
 */
public interface AdditionalSecurityScheme extends SecurityScheme {

    /**
     * Creates a new AdditionalSecurityScheme from the specified JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object representing the security scheme.
     * @return the AdditionalSecurityScheme.
     */
    static AdditionalSecurityScheme fromJson(final String securitySchemeName, final JsonObject jsonObject) {
        return new ImmutableAdditionalSecurityScheme(securitySchemeName, jsonObject);
    }

    /**
     * Creates a new builder for building an AdditionalSecurityScheme.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param contextExtensionScopedScheme the context extension scoped scheme identifier.
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static AdditionalSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName,
            final CharSequence contextExtensionScopedScheme) {
        return AdditionalSecurityScheme.Builder.newBuilder(securitySchemeName, contextExtensionScopedScheme);
    }

    /**
     * Creates a new builder for building an AdditionalSecurityScheme, initialized with the values from the specified
     * JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param contextExtensionScopedScheme the context extension scoped scheme identifier.
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static AdditionalSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName,
            final CharSequence contextExtensionScopedScheme,
            final JsonObject jsonObject) {
        return AdditionalSecurityScheme.Builder.newBuilder(securitySchemeName, contextExtensionScopedScheme, jsonObject);
    }

    /**
     * A mutable builder with a fluent API for building an {@link AdditionalSecurityScheme}.
     */
    interface Builder extends SecurityScheme.Builder<AdditionalSecurityScheme.Builder, AdditionalSecurityScheme> {

        /**
         * Creates a new builder for building an AdditionalSecurityScheme.
         *
         * @param securitySchemeName the name of the security scheme.
         * @param contextExtensionScopedScheme the context extension scoped scheme identifier.
         * @return the builder.
         */
        static AdditionalSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName,
                final CharSequence contextExtensionScopedScheme) {
            final SecuritySchemeScheme scheme = SecuritySchemeScheme.of(
                    checkNotNull(contextExtensionScopedScheme, "contextExtensionScopedScheme"));
            final MutableAdditionalSecuritySchemeBuilder additionalSecuritySchemeBuilder =
                    new MutableAdditionalSecuritySchemeBuilder(
                            checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                            scheme,
                            JsonObject.newBuilder());
            return additionalSecuritySchemeBuilder.setScheme(scheme);
        }

        /**
         * Creates a new builder for building an AdditionalSecurityScheme, initialized with the values from the
         * specified JSON object.
         *
         * @param securitySchemeName the name of the security scheme.
         * @param contextExtensionScopedScheme the context extension scoped scheme identifier.
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static AdditionalSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName,
                final CharSequence contextExtensionScopedScheme, final JsonObject jsonObject) {
            return new MutableAdditionalSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    SecuritySchemeScheme.of(
                            checkNotNull(contextExtensionScopedScheme, "contextExtensionScopedScheme")),
                    jsonObject.toBuilder());
        }

    }
}
