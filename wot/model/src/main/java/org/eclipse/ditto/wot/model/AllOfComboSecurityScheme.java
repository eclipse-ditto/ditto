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

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * An AllOfComboSecurityScheme is a {@link ComboSecurityScheme} in which "all of" the specified linked
 * {@link SecurityScheme}s must apply.
 *
 * @since 2.4.0
 */
public interface AllOfComboSecurityScheme extends ComboSecurityScheme {

    static AllOfComboSecurityScheme fromJson(final String securitySchemeName, final JsonObject jsonObject) {
        return new ImmutableAllOfComboSecurityScheme(securitySchemeName, jsonObject);
    }

    static AllOfComboSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
        return AllOfComboSecurityScheme.Builder.newBuilder(securitySchemeName);
    }

    static AllOfComboSecurityScheme.Builder newBuilder(final CharSequence securitySchemeName,
            final JsonObject jsonObject) {
        return AllOfComboSecurityScheme.Builder.newBuilder(securitySchemeName, jsonObject);
    }

    @Override
    default SecuritySchemeScheme getScheme() {
        return SecuritySchemeScheme.COMBO;
    }

    List<String> getAllOf();


    interface Builder extends SecurityScheme.Builder<Builder, AllOfComboSecurityScheme> {

        static Builder newBuilder(final CharSequence securitySchemeName) {
            return new MutableAllOfComboSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    JsonObject.newBuilder());
        }

        static Builder newBuilder(final CharSequence securitySchemeName,
                final JsonObject jsonObject) {
            return new MutableAllOfComboSecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    jsonObject.toBuilder());
        }

        Builder setAllOf(@Nullable Collection<SecurityScheme> securitySchemes);

    }

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonFieldDefinition}s of a AllOfComboSecurityScheme.
     */
    @Immutable
    final class JsonFields {

        public static final JsonFieldDefinition<JsonArray> ALL_OF = JsonFactory.newJsonArrayFieldDefinition(
                "allOf");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
