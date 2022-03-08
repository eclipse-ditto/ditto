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
 * A BearerSecurityScheme is a {@link SecurityScheme} indicating to use {@code Bearer Tokens} "independently of OAuth2".
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bib-rfc6750">RFC6750 - Bearer Token</a>
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bearersecurityscheme">WoT TD BearerSecurityScheme</a>
 * @since 2.4.0
 */
public interface BearerSecurityScheme extends SecurityScheme {

    static BearerSecurityScheme fromJson(final String securitySchemeName, final JsonObject jsonObject) {
        return new ImmutableBearerSecurityScheme(securitySchemeName, jsonObject);
    }

    static BearerSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
        return BearerSecurityScheme.Builder.newBuilder(securitySchemeName);
    }

    static BearerSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
        return BearerSecurityScheme.Builder.newBuilder(securitySchemeName, jsonObject);
    }

    @Override
    default SecuritySchemeScheme getScheme() {
        return SecuritySchemeScheme.BEARER;
    }

    Optional<IRI> getAuthorization();

    Optional<String> getAlg();

    Optional<String> getFormat();

    Optional<SecuritySchemeIn> getIn();

    Optional<String> getName();


    interface Builder extends SecurityScheme.Builder<Builder, BearerSecurityScheme> {

        static Builder newBuilder(final CharSequence securitySchemeName) {
            return new MutableBearerSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    JsonObject.newBuilder());
        }

        static Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
            return new MutableBearerSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    jsonObject.toBuilder());
        }

        Builder setAuthorization(@Nullable IRI authorization);

        Builder setAlg(@Nullable String alg);

        Builder setFormat(@Nullable String format);

        Builder setIn(@Nullable String in);

        Builder setName(@Nullable String name);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a BearerSecurityScheme.
     */
    @Immutable
    final class JsonFields {

        public static final JsonFieldDefinition<String> AUTHORIZATION = JsonFactory.newStringFieldDefinition(
                "authorization");

        public static final JsonFieldDefinition<String> ALG = JsonFactory.newStringFieldDefinition(
                "alg");

        public static final JsonFieldDefinition<String> FORMAT = JsonFactory.newStringFieldDefinition(
                "format");

        public static final JsonFieldDefinition<String> IN = JsonFactory.newStringFieldDefinition(
                "in");

        public static final JsonFieldDefinition<String> NAME = JsonFactory.newStringFieldDefinition(
                "name");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
