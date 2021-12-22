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

import java.util.Arrays;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * A DigestSecurityScheme is a {@link SecurityScheme} indicating to use {@code Digest Access} Authentication.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bib-rfc7616">RFC7617 - Digest Access Authentication</a>
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#digestsecurityscheme">WoT TD DigestSecurityScheme</a>
 * @since 2.4.0
 */
public interface DigestSecurityScheme extends SecurityScheme {

    static DigestSecurityScheme fromJson(final String securitySchemeName, final JsonObject jsonObject) {
        return new ImmutableDigestSecurityScheme(securitySchemeName, jsonObject);
    }

    static DigestSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
        return DigestSecurityScheme.Builder.newBuilder(securitySchemeName);
    }

    static DigestSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
        return DigestSecurityScheme.Builder.newBuilder(securitySchemeName, jsonObject);
    }

    @Override
    default SecuritySchemeScheme getScheme() {
        return SecuritySchemeScheme.DIGEST;
    }

    Optional<Qop> getQop();

    Optional<SecuritySchemeIn> getIn();
    
    Optional<String> getName();


    interface Builder extends SecurityScheme.Builder<Builder, DigestSecurityScheme> {

        static Builder newBuilder(final CharSequence securitySchemeName) {
            return new MutableDigestSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    JsonObject.newBuilder());
        }

        static Builder newBuilder(final CharSequence securitySchemeName,
                final JsonObject jsonObject) {
            return new MutableDigestSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    jsonObject.toBuilder());
        }

        Builder setQop(@Nullable Qop qop);

        Builder setIn(@Nullable String in);

        Builder setName(@Nullable String name);

    }
    
    enum Qop implements CharSequence {
        AUTH("auth"),
        AUTH_INT("auth-int");

        private final String name;

        Qop(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        /**
         * Returns the {@code Qop} for the given {@code name} if it exists.
         *
         * @param name the name.
         * @return the Qop or an empty optional.
         */
        public static Optional<Qop> forName(final CharSequence name) {
            checkNotNull(name, "name");
            return Arrays.stream(values())
                    .filter(c -> c.name.contentEquals(name))
                    .findFirst();
        }

        @Override
        public int length() {
            return name.length();
        }

        @Override
        public char charAt(final int index) {
            return name.charAt(index);
        }

        @Override
        public CharSequence subSequence(final int start, final int end) {
            return name.subSequence(start, end);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a DigestSecurityScheme.
     */
    @Immutable
    final class JsonFields {

        public static final JsonFieldDefinition<String> QOP = JsonFactory.newStringFieldDefinition(
                "qop");

        public static final JsonFieldDefinition<String> IN = JsonFactory.newStringFieldDefinition(
                "in");

        public static final JsonFieldDefinition<String> NAME = JsonFactory.newStringFieldDefinition(
                "name");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
