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
 * A BasicSecurityScheme is a {@link SecurityScheme} indicating to use {@code Basic} Authentication, using an unencryped
 * username and password.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bib-rfc7617">RFC7617 - Basic Authentication</a>
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#basicsecurityscheme">WoT TD BasicSecurityScheme</a>
 * @since 2.4.0
 */
public interface BasicSecurityScheme extends SecurityScheme {

    static BasicSecurityScheme fromJson(final String securitySchemeName, final JsonObject jsonObject) {
        return new ImmutableBasicSecurityScheme(securitySchemeName, jsonObject);
    }

    static BasicSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
        return BasicSecurityScheme.Builder.newBuilder(securitySchemeName);
    }

    static BasicSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
        return BasicSecurityScheme.Builder.newBuilder(securitySchemeName, jsonObject);
    }

    @Override
    default SecuritySchemeScheme getScheme() {
        return SecuritySchemeScheme.BASIC;
    }

    Optional<SecuritySchemeIn> getIn();

    Optional<String> getName();


    interface Builder extends SecurityScheme.Builder<BasicSecurityScheme.Builder, BasicSecurityScheme> {

        static BasicSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
            return new MutableBasicSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    JsonObject.newBuilder());
        }

        static BasicSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName,
                final JsonObject jsonObject) {
            return new MutableBasicSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    jsonObject.toBuilder());
        }

        Builder setIn(@Nullable String in);

        Builder setName(@Nullable String name);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a BasicSecurityScheme.
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
