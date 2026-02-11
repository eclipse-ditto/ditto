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
 * An AutoSecurityScheme is a {@link SecurityScheme} indicating that the security parameters are going to be negotiated
 * by the underlying protocols at runtime, subject to the respective specifications for the protocol.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#autosecurityscheme">WoT TD AutoSecurityScheme</a>
 * @since 3.0.0
 */
public interface AutoSecurityScheme extends SecurityScheme {

    /**
     * Creates a new AutoSecurityScheme from the specified JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object representing the security scheme.
     * @return the AutoSecurityScheme.
     */
    static AutoSecurityScheme fromJson(final String securitySchemeName, final JsonObject jsonObject) {
        return new ImmutableAutoSecurityScheme(securitySchemeName, jsonObject);
    }

    /**
     * Creates a new builder for building an AutoSecurityScheme.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     * @throws NullPointerException if {@code securitySchemeName} is {@code null}.
     */
    static AutoSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
        return AutoSecurityScheme.Builder.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building an AutoSecurityScheme, initialized with the values from the specified
     * JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if {@code securitySchemeName} is {@code null}.
     */
    static AutoSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
        return AutoSecurityScheme.Builder.newBuilder(securitySchemeName, jsonObject);
    }

    @Override
    default SecuritySchemeScheme getScheme() {
        return SecuritySchemeScheme.AUTO;
    }

    /**
     * A mutable builder with a fluent API for building an {@link AutoSecurityScheme}.
     */
    interface Builder extends SecurityScheme.Builder<AutoSecurityScheme.Builder, AutoSecurityScheme> {

        /**
         * Creates a new builder for building an AutoSecurityScheme.
         *
         * @param securitySchemeName the name of the security scheme.
         * @return the builder.
         */
        static AutoSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
            return new MutableAutoSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building an AutoSecurityScheme, initialized with the values from the
         * specified JSON object.
         *
         * @param securitySchemeName the name of the security scheme.
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static AutoSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName,
                final JsonObject jsonObject) {
            return new MutableAutoSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    jsonObject.toBuilder());
        }

    }
}
