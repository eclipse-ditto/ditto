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

    static PskSecurityScheme fromJson(final String securitySchemeName, final JsonObject jsonObject) {
        return new ImmutablePskSecurityScheme(securitySchemeName, jsonObject);
    }

    static PskSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
        return PskSecurityScheme.Builder.newBuilder(securitySchemeName);
    }

    static PskSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
        return PskSecurityScheme.Builder.newBuilder(securitySchemeName, jsonObject);
    }

    @Override
    default SecuritySchemeScheme getScheme() {
        return SecuritySchemeScheme.PSK;
    }

    Optional<String> getIdentity();


    interface Builder extends SecurityScheme.Builder<Builder, PskSecurityScheme> {

        static Builder newBuilder(final CharSequence securitySchemeName) {
            return new MutablePskSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    JsonObject.newBuilder());
        }

        static Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
            return new MutablePskSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    jsonObject.toBuilder());
        }

        Builder setIdentity(@Nullable String identity);

    }
    
    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a PskSecurityScheme.
     */
    @Immutable
    final class JsonFields {

        public static final JsonFieldDefinition<String> IDENTITY = JsonFactory.newStringFieldDefinition(
                "identity");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
