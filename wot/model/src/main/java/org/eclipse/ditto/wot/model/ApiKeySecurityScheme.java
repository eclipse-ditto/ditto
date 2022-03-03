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
 * An ApiKeySecurityScheme is a {@link SecurityScheme} indicating to use an API key / API token, "for example when a key
 * in an unknown or proprietary format is provided by a cloud service provider."
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#apikeysecurityscheme">WoT TD APIKeySecurityScheme</a>
 * @since 2.4.0
 */
public interface ApiKeySecurityScheme extends SecurityScheme {

    static ApiKeySecurityScheme fromJson(final String securitySchemeName, final JsonObject jsonObject) {
        return new ImmutableApiKeySecurityScheme(securitySchemeName, jsonObject);
    }

    static ApiKeySecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
        return ApiKeySecurityScheme.Builder.newBuilder(securitySchemeName);
    }

    static ApiKeySecurityScheme.Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
        return ApiKeySecurityScheme.Builder.newBuilder(securitySchemeName, jsonObject);
    }

    @Override
    default SecuritySchemeScheme getScheme() {
        return SecuritySchemeScheme.APIKEY;
    }

    Optional<SecuritySchemeIn> getIn();

    Optional<String> getName();


    interface Builder extends SecurityScheme.Builder<Builder, ApiKeySecurityScheme> {

        static Builder newBuilder(final CharSequence securitySchemeName) {
            return new MutableApiKeySecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    JsonObject.newBuilder());
        }

        static Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
            return new MutableApiKeySecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    jsonObject.toBuilder());
        }

        ApiKeySecurityScheme.Builder setIn(@Nullable String in);

        ApiKeySecurityScheme.Builder setName(@Nullable String name);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of an ApiKeySecurityScheme.
     */
    @Immutable
    final class JsonFields {

        public static final JsonFieldDefinition<String> IN = JsonFactory.newStringFieldDefinition(
                "in");

        public static final JsonFieldDefinition<String> NAME = JsonFactory.newStringFieldDefinition(
                "name");

        private JsonFields() {
            throw new AssertionError();
        }
    }

}
