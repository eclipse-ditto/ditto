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

import org.eclipse.ditto.json.JsonObject;

/**
 * A ComboSecurityScheme is a {@link SecurityScheme} combining other defined {@link SecurityScheme}s.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#combosecurityscheme">WoT TD ComboSecurityScheme</a>
 * @since 2.4.0
 */
public interface ComboSecurityScheme extends SecurityScheme {

    /**
     * Creates a new ComboSecurityScheme from the specified JSON object.
     * <p>
     * Automatically determines whether this is a "oneOf" or "allOf" combo scheme based on the JSON content.
     * </p>
     *
     * @param securitySchemeName the name of the security scheme.
     * @param jsonObject the JSON object representing the security scheme.
     * @return the ComboSecurityScheme (either {@link OneOfComboSecurityScheme} or {@link AllOfComboSecurityScheme}).
     * @throws IllegalArgumentException if the JSON object contains neither "oneOf" nor "allOf".
     */
    static ComboSecurityScheme fromJson(final String securitySchemeName, final JsonObject jsonObject) {
        if (jsonObject.getValue(OneOfComboSecurityScheme.JsonFields.ONE_OF).isPresent()) {
            return OneOfComboSecurityScheme.fromJson(securitySchemeName, jsonObject);
        } else if (jsonObject.getValue(AllOfComboSecurityScheme.JsonFields.ALL_OF).isPresent()) {
            return AllOfComboSecurityScheme.fromJson(securitySchemeName, jsonObject);
        }
        throw new IllegalArgumentException("Unsupported ComboSecurityScheme config");
    }

    /**
     * Creates a new builder for building a {@link OneOfComboSecurityScheme}.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     */
    static OneOfComboSecurityScheme.Builder newOneOfBuilder(final CharSequence securitySchemeName) {
        return OneOfComboSecurityScheme.Builder.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building an {@link AllOfComboSecurityScheme}.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     */
    static AllOfComboSecurityScheme.Builder newAllOfBuilder(final CharSequence securitySchemeName) {
        return AllOfComboSecurityScheme.Builder.newBuilder(securitySchemeName);
    }

    @Override
    default SecuritySchemeScheme getScheme() {
        return SecuritySchemeScheme.COMBO;
    }

}
