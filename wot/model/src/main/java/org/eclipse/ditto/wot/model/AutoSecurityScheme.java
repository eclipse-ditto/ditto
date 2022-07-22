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

    static AutoSecurityScheme fromJson(final String securitySchemeName, final JsonObject jsonObject) {
        return new ImmutableAutoSecurityScheme(securitySchemeName, jsonObject);
    }

    static AutoSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
        return AutoSecurityScheme.Builder.newBuilder(securitySchemeName);
    }

    static AutoSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
        return AutoSecurityScheme.Builder.newBuilder(securitySchemeName, jsonObject);
    }

    @Override
    default SecuritySchemeScheme getScheme() {
        return SecuritySchemeScheme.AUTO;
    }

    interface Builder extends SecurityScheme.Builder<AutoSecurityScheme.Builder, AutoSecurityScheme> {

        static AutoSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
            return new MutableAutoSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    JsonObject.newBuilder());
        }

        static AutoSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName,
                final JsonObject jsonObject) {
            return new MutableAutoSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    jsonObject.toBuilder());
        }

    }
}
