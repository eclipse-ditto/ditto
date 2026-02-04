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
 * A SecurityScheme describes the configuration of a security mechanism.
 * <p>
 * Security schemes define how Consumers authenticate and authorize interactions with the Thing.
 * It has the following subclasses representing different authentication mechanisms:
 * </p>
 * <ul>
 *     <li>{@link NoSecurityScheme} - no authentication required</li>
 *     <li>{@link AutoSecurityScheme} - automatically selected security mechanism</li>
 *     <li>{@link ComboSecurityScheme} - combination of multiple security schemes</li>
 *     <li>{@link BasicSecurityScheme} - HTTP Basic Authentication</li>
 *     <li>{@link DigestSecurityScheme} - HTTP Digest Authentication</li>
 *     <li>{@link ApiKeySecurityScheme} - API key-based authentication</li>
 *     <li>{@link BearerSecurityScheme} - Bearer token authentication</li>
 *     <li>{@link PskSecurityScheme} - Pre-Shared Key authentication</li>
 *     <li>{@link OAuth2SecurityScheme} - OAuth 2.0 authentication</li>
 *     <li>{@link AdditionalSecurityScheme} - custom security schemes from context extensions</li>
 * </ul>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#securityscheme">WoT TD SecurityScheme</a>
 * @since 2.4.0
 */
public interface SecurityScheme extends TypedJsonObject<SecurityScheme>, Jsonifiable<JsonObject> {

    /**
     * Creates a SecurityScheme from the specified JSON object, automatically determining the correct
     * subtype based on the {@code scheme} field.
     *
     * @param securitySchemeName the name of the security scheme (the key in the securityDefinitions map).
     * @param jsonObject the JSON object representing the security scheme.
     * @return the appropriate SecurityScheme subtype.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if the scheme type is missing or unknown.
     */
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
                .orElseThrow(() -> new IllegalArgumentException("Could not create SecurityScheme - " +
                        "json field <" + SecuritySchemeJsonFields.SCHEME.getPointer() + "> was missing or unknown"));
    }

    /**
     * Creates a new builder for building a {@link NoSecurityScheme}.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#nosecurityscheme">WoT TD NoSecurityScheme</a>
     */
    static NoSecurityScheme.Builder newNoSecurityBuilder(final CharSequence securitySchemeName) {
        return NoSecurityScheme.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building an {@link AutoSecurityScheme}.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#autosecurityscheme">WoT TD AutoSecurityScheme</a>
     */
    static AutoSecurityScheme.Builder newAutoSecurityBuilder(final CharSequence securitySchemeName) {
        return AutoSecurityScheme.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building an {@link AllOfComboSecurityScheme} where all referenced
     * security schemes must be satisfied.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#combosecurityscheme">WoT TD ComboSecurityScheme</a>
     */
    static AllOfComboSecurityScheme.Builder newAllOfComboSecurityBuilder(final CharSequence securitySchemeName) {
        return AllOfComboSecurityScheme.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building a {@link OneOfComboSecurityScheme} where one of the referenced
     * security schemes must be satisfied.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#combosecurityscheme">WoT TD ComboSecurityScheme</a>
     */
    static OneOfComboSecurityScheme.Builder newOneOfComboSecurityBuilder(final CharSequence securitySchemeName) {
        return OneOfComboSecurityScheme.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building a {@link BasicSecurityScheme} for HTTP Basic Authentication.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#basicsecurityscheme">WoT TD BasicSecurityScheme</a>
     */
    static BasicSecurityScheme.Builder newBasicSecurityBuilder(final CharSequence securitySchemeName) {
        return BasicSecurityScheme.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building a {@link DigestSecurityScheme} for HTTP Digest Authentication.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#digestsecurityscheme">WoT TD DigestSecurityScheme</a>
     */
    static DigestSecurityScheme.Builder newDigestSecurityBuilder(final CharSequence securitySchemeName) {
        return DigestSecurityScheme.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building an {@link ApiKeySecurityScheme} for API key-based authentication.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#apikeysecurityscheme">WoT TD APIKeySecurityScheme</a>
     */
    static ApiKeySecurityScheme.Builder newApiKeySecurityBuilder(final CharSequence securitySchemeName) {
        return ApiKeySecurityScheme.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building a {@link BearerSecurityScheme} for Bearer token authentication.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bearersecurityscheme">WoT TD BearerSecurityScheme</a>
     */
    static BearerSecurityScheme.Builder newBearerSecurityBuilder(final CharSequence securitySchemeName) {
        return BearerSecurityScheme.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building a {@link PskSecurityScheme} for Pre-Shared Key authentication.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#psksecurityscheme">WoT TD PSKSecurityScheme</a>
     */
    static PskSecurityScheme.Builder newPskSecurityBuilder(final CharSequence securitySchemeName) {
        return PskSecurityScheme.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building an {@link OAuth2SecurityScheme} for OAuth 2.0 authentication.
     *
     * @param securitySchemeName the name of the security scheme.
     * @return the builder.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#oauth2securityscheme">WoT TD OAuth2SecurityScheme</a>
     */
    static OAuth2SecurityScheme.Builder newOAuth2SecurityBuilder(final CharSequence securitySchemeName) {
        return OAuth2SecurityScheme.newBuilder(securitySchemeName);
    }

    /**
     * Creates a new builder for building an {@link AdditionalSecurityScheme} defined via context extensions.
     *
     * @param securitySchemeName the name of the security scheme.
     * @param contextExtensionScopedScheme the scheme name as defined in the context extension.
     * @return the builder.
     */
    static AdditionalSecurityScheme.Builder newAdditionalSecurityBuilder(final CharSequence securitySchemeName,
            final CharSequence contextExtensionScopedScheme) {
        return AdditionalSecurityScheme.newBuilder(securitySchemeName, contextExtensionScopedScheme);
    }

    /**
     * Returns the name of this security scheme as defined in the Thing Description's securityDefinitions map.
     *
     * @return the security scheme name.
     */
    String getSecuritySchemeName();

    /**
     * Returns the optional JSON-LD {@code @type} providing semantic annotations for this security scheme.
     *
     * @return the optional semantic type annotation.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#securityscheme">WoT TD SecurityScheme (@type)</a>
     */
    Optional<AtType> getAtType();

    /**
     * Returns the security scheme identifier (e.g., "nosec", "basic", "oauth2").
     *
     * @return the security scheme type.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#securityscheme">WoT TD SecurityScheme (scheme)</a>
     */
    SecuritySchemeScheme getScheme();

    /**
     * Returns the optional human-readable description of this security scheme, based on the default language.
     *
     * @return the optional description.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#securityscheme">WoT TD SecurityScheme (description)</a>
     */
    Optional<Description> getDescription();

    /**
     * Returns the optional multi-language map of human-readable descriptions for this security scheme.
     *
     * @return the optional multi-language descriptions.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#multilanguage">WoT TD MultiLanguage</a>
     */
    Optional<Descriptions> getDescriptions();

    /**
     * Returns the optional URI of the proxy server that must be traversed for authentication.
     *
     * @return the optional proxy URI.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#securityscheme">WoT TD SecurityScheme (proxy)</a>
     */
    Optional<IRI> getProxy();


    /**
     * A mutable builder for creating {@link SecurityScheme} instances.
     *
     * @param <B> the type of the Builder.
     * @param <S> the type of the SecurityScheme.
     */
    interface Builder<B extends Builder<B, S>, S extends SecurityScheme> extends TypedJsonObjectBuilder<B, S> {

        /**
         * Sets the JSON-LD {@code @type} for semantic annotations.
         *
         * @param atType the semantic type, or {@code null} to remove.
         * @return this builder.
         */
        B setAtType(@Nullable AtType atType);

        /**
         * Sets the security scheme type identifier.
         *
         * @param scheme the scheme type, or {@code null} to remove.
         * @return this builder.
         */
        B setScheme(@Nullable SecuritySchemeScheme scheme);

        /**
         * Sets the human-readable description.
         *
         * @param description the description, or {@code null} to remove.
         * @return this builder.
         */
        B setDescription(@Nullable Description description);

        /**
         * Sets the multi-language descriptions.
         *
         * @param descriptions the descriptions map, or {@code null} to remove.
         * @return this builder.
         */
        B setDescriptions(@Nullable Descriptions descriptions);

        /**
         * Sets the proxy URI.
         *
         * @param proxy the proxy URI, or {@code null} to remove.
         * @return this builder.
         */
        B setProxy(@Nullable IRI proxy);

        /**
         * Builds the SecurityScheme.
         *
         * @return the built SecurityScheme instance.
         */
        S build();
    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a SecurityScheme.
     */
    @Immutable
    final class SecuritySchemeJsonFields {

        /**
         * JSON field definition for the JSON-LD type (single value).
         */
        public static final JsonFieldDefinition<String> AT_TYPE = JsonFactory.newStringFieldDefinition(
                "@type");

        /**
         * JSON field definition for the JSON-LD type (multiple values).
         */
        public static final JsonFieldDefinition<JsonArray> AT_TYPE_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "@type");

        /**
         * JSON field definition for the security scheme type.
         */
        public static final JsonFieldDefinition<String> SCHEME = JsonFactory.newStringFieldDefinition(
                "scheme");

        /**
         * JSON field definition for the description.
         */
        public static final JsonFieldDefinition<String> DESCRIPTION = JsonFactory.newStringFieldDefinition(
                "description");

        /**
         * JSON field definition for the multilingual descriptions.
         */
        public static final JsonFieldDefinition<JsonObject> DESCRIPTIONS = JsonFactory.newJsonObjectFieldDefinition(
                "descriptions");

        /**
         * JSON field definition for the proxy URI.
         */
        public static final JsonFieldDefinition<String> PROXY = JsonFactory.newStringFieldDefinition(
                "proxy");

        private SecuritySchemeJsonFields() {
            throw new AssertionError();
        }
    }
}
