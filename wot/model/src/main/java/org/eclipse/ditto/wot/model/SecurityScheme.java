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
import static org.eclipse.ditto.wot.model.SecuritySchemeScheme.APIKEY;
import static org.eclipse.ditto.wot.model.SecuritySchemeScheme.AUTO;
import static org.eclipse.ditto.wot.model.SecuritySchemeScheme.BASIC;
import static org.eclipse.ditto.wot.model.SecuritySchemeScheme.BEARER;
import static org.eclipse.ditto.wot.model.SecuritySchemeScheme.COMBO;
import static org.eclipse.ditto.wot.model.SecuritySchemeScheme.DIGEST;
import static org.eclipse.ditto.wot.model.SecuritySchemeScheme.NOSEC;
import static org.eclipse.ditto.wot.model.SecuritySchemeScheme.OAUTH2;
import static org.eclipse.ditto.wot.model.SecuritySchemeScheme.PSK;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * A SecuritySchema describes the configuration of a security mechanism.
 * It has the following subclasses:
 * <ul>
 *     <li>{@link NoSecurityScheme}</li>
 *     <li>{@link AutoSecurityScheme}</li>
 *     <li>{@link ComboSecurityScheme}</li>
 *     <li>{@link BasicSecurityScheme}</li>
 *     <li>{@link DigestSecurityScheme}</li>
 *     <li>{@link ApiKeySecurityScheme}</li>
 *     <li>{@link BearerSecurityScheme}</li>
 *     <li>{@link PskSecurityScheme}</li>
 *     <li>{@link OAuth2SecurityScheme}</li>
 *     <li>{@link AdditionalSecurityScheme}</li>
 * </ul>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#securityscheme">WoT TD SecurityScheme</a>
 * @since 2.4.0
 */
public interface SecurityScheme extends TypedJsonObject<SecurityScheme>, Jsonifiable<JsonObject> {

    static SecurityScheme fromJson(final CharSequence securitySchemeName, final JsonObject jsonObject) {
        final String schemeName = checkNotNull(securitySchemeName, "securitySchemeName").toString();
        return jsonObject.getValue(SecuritySchemeJsonFields.SCHEME)
                .map(SecuritySchemeScheme::of)
                .map(type -> {
                    if (type.equals(NOSEC)) {
                        return NoSecurityScheme.fromJson(schemeName, jsonObject);
                    } else if (type.equals(AUTO)) {
                        return AutoSecurityScheme.fromJson(schemeName, jsonObject);
                    } else if (type.equals(COMBO)) {
                        return ComboSecurityScheme.fromJson(schemeName, jsonObject);
                    } else if (type.equals(BASIC)) {
                        return BasicSecurityScheme.fromJson(schemeName, jsonObject);
                    } else if (type.equals(DIGEST)) {
                        return DigestSecurityScheme.fromJson(schemeName, jsonObject);
                    } else if (type.equals(APIKEY)) {
                        return ApiKeySecurityScheme.fromJson(schemeName, jsonObject);
                    } else if (type.equals(BEARER)) {
                        return BearerSecurityScheme.fromJson(schemeName, jsonObject);
                    } else if (type.equals(PSK)) {
                        return PskSecurityScheme.fromJson(schemeName, jsonObject);
                    } else if (type.equals(OAUTH2)) {
                        return OAuth2SecurityScheme.fromJson(schemeName, jsonObject);
                    } else {
                        return AdditionalSecurityScheme.fromJson(schemeName, jsonObject);
                    }
                })
                .orElseThrow(() -> new IllegalArgumentException("Could not create SingleDataSchema"));
    }

    static NoSecurityScheme.Builder newNoSecurityBuilder(final CharSequence securitySchemeName) {
        return NoSecurityScheme.newBuilder(securitySchemeName);
    }

    static AutoSecurityScheme.Builder newAutoSecurityBuilder(final CharSequence securitySchemeName) {
        return AutoSecurityScheme.newBuilder(securitySchemeName);
    }

    static AllOfComboSecurityScheme.Builder newAllOfComboSecurityBuilder(final CharSequence securitySchemeName) {
        return AllOfComboSecurityScheme.newBuilder(securitySchemeName);
    }

    static OneOfComboSecurityScheme.Builder newOneOfComboSecurityBuilder(final CharSequence securitySchemeName) {
        return OneOfComboSecurityScheme.newBuilder(securitySchemeName);
    }

    static BasicSecurityScheme.Builder newBasicSecurityBuilder(final CharSequence securitySchemeName) {
        return BasicSecurityScheme.newBuilder(securitySchemeName);
    }

    static DigestSecurityScheme.Builder newDigestSecurityBuilder(final CharSequence securitySchemeName) {
        return DigestSecurityScheme.newBuilder(securitySchemeName);
    }

    static ApiKeySecurityScheme.Builder newApiKeySecurityBuilder(final CharSequence securitySchemeName) {
        return ApiKeySecurityScheme.newBuilder(securitySchemeName);
    }

    static BearerSecurityScheme.Builder newBearerSecurityBuilder(final CharSequence securitySchemeName) {
        return BearerSecurityScheme.newBuilder(securitySchemeName);
    }

    static PskSecurityScheme.Builder newPskSecurityBuilder(final CharSequence securitySchemeName) {
        return PskSecurityScheme.newBuilder(securitySchemeName);
    }

    static OAuth2SecurityScheme.Builder newOAuth2SecurityBuilder(final CharSequence securitySchemeName) {
        return OAuth2SecurityScheme.newBuilder(securitySchemeName);
    }

    static AdditionalSecurityScheme.Builder newAdditionalSecurityBuilder(final CharSequence securitySchemeName,
            final CharSequence contextExtensionScopedScheme) {
        return AdditionalSecurityScheme.newBuilder(securitySchemeName, contextExtensionScopedScheme);
    }

    String getSecuritySchemeName();

    Optional<AtType> getAtType();

    SecuritySchemeScheme getScheme();

    Optional<Description> getDescription();

    Optional<Descriptions> getDescriptions();

    Optional<IRI> getProxy();


    interface Builder<B extends Builder<B, S>, S extends SecurityScheme> extends TypedJsonObjectBuilder<B, S> {

        B setAtType(@Nullable AtType atType);

        B setScheme(@Nullable SecuritySchemeScheme scheme);

        B setDescription(@Nullable Description description);

        B setDescriptions(@Nullable Descriptions descriptions);

        B setProxy(@Nullable IRI proxy);

        S build();
    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a SecurityScheme.
     */
    @Immutable
    final class SecuritySchemeJsonFields {

        public static final JsonFieldDefinition<String> AT_TYPE = JsonFactory.newStringFieldDefinition(
                "@type");

        public static final JsonFieldDefinition<JsonArray> AT_TYPE_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "@type");

        public static final JsonFieldDefinition<String> SCHEME = JsonFactory.newStringFieldDefinition(
                "scheme");

        public static final JsonFieldDefinition<String> DESCRIPTION = JsonFactory.newStringFieldDefinition(
                "description");

        public static final JsonFieldDefinition<JsonObject> DESCRIPTIONS = JsonFactory.newJsonObjectFieldDefinition(
                "descriptions");

        public static final JsonFieldDefinition<String> PROXY = JsonFactory.newStringFieldDefinition(
                "proxy");

        private SecuritySchemeJsonFields() {
            throw new AssertionError();
        }
    }
}
