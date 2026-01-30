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
 * A NoSecurityScheme is a {@link SecurityScheme} indicating there is no authentication or other mechanism required to
 * access the resource.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#nosecurityscheme">WoT TD NoSecurityScheme</a>
 * @since 2.4.0
 */
public interface NoSecurityScheme extends SecurityScheme {

    /**
     * Creates a new NoSecurityScheme from the specified JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object representing the security scheme.
     * @return the NoSecurityScheme.
     */
    static NoSecurityScheme fromJson(final String securitySchemeName, final JsonObject jsonObject) {
        return new ImmutableNoSecurityScheme(securitySchemeName, jsonObject);
    }

    /**
     * Creates a new builder for building a NoSecurityScheme.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     * @throws NullPointerException if {@code securitySchemeName} is {@code null}.
     */
    static NoSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
        return NoSecurityScheme.Builder.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building a NoSecurityScheme, initialized with the values from the specified
     * JSON object.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if {@code securitySchemeName} is {@code null}.
     */
    static NoSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
        return NoSecurityScheme.Builder.newBuilder(securitySchemeName, jsonObject);
    }

    @Override
    default SecuritySchemeScheme getScheme() {
        return SecuritySchemeScheme.NOSEC;
    }

    /**
     * A mutable builder with a fluent API for building a {@link NoSecurityScheme}.
     */
    interface Builder extends SecurityScheme.Builder<Builder, NoSecurityScheme> {

        /**
         * Creates a new builder for building a NoSecurityScheme.
         *
         * @param securitySchemeName the name of the security scheme.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence securitySchemeName) {
            return new MutableNoSecuritySchemeBuilder(checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building a NoSecurityScheme, initialized with the values from the
         * specified JSON object.
         *
         * @param securitySchemeName the name of the security scheme.
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
            return new MutableNoSecuritySchemeBuilder(checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    jsonObject.toBuilder());
        }

    }
}
