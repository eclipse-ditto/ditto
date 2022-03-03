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

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * An OAuth2SecurityScheme is a {@link SecurityScheme} indicating to use {@code OAuth 2.0} for authentication.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bib-rfc6749">RFC6749 - The OAuth 2.0 Authorization Framework</a>
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bib-rfc8252">RFC8252 - OAuth 2.0 for native Apps</a>
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bib-rfc8628">RFC8628 - OAuth 2.0 Device Authorization Grant</a>
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#oauth2securityscheme">WoT TD OAuth2SecurityScheme</a>
 * @since 2.4.0
 */
public interface OAuth2SecurityScheme extends SecurityScheme {

    static OAuth2SecurityScheme fromJson(final String securitySchemeName, final JsonObject jsonObject) {
        return new ImmutableOAuth2SecurityScheme(securitySchemeName, jsonObject);
    }

    static OAuth2SecurityScheme.Builder newBuilder(final CharSequence securitySchemeName) {
        return OAuth2SecurityScheme.Builder.newBuilder(securitySchemeName);
    }

    static OAuth2SecurityScheme.Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
        return OAuth2SecurityScheme.Builder.newBuilder(securitySchemeName, jsonObject);
    }

    @Override
    default SecuritySchemeScheme getScheme() {
        return SecuritySchemeScheme.OAUTH2;
    }

    Optional<IRI> getAuthorization();

    Optional<IRI> getToken();

    Optional<IRI> getRefresh();

    Optional<OAuth2Scopes> getScopes();

    Optional<OAuth2Flow> getFlow();


    interface Builder extends SecurityScheme.Builder<Builder, OAuth2SecurityScheme> {

        static Builder newBuilder(final CharSequence securitySchemeName) {
            return new MutableOAuth2SecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    JsonObject.newBuilder());
        }

        static Builder newBuilder(final CharSequence securitySchemeName, final JsonObject jsonObject) {
            return new MutableOAuth2SecuritySchemeBuilder(
                    checkNotNull(securitySchemeName, "securitySchemeName").toString(),
                    jsonObject.toBuilder());
        }

        Builder setAuthorization(@Nullable IRI authorization);

        Builder setToken(@Nullable IRI token);

        Builder setRefresh(@Nullable IRI refresh);

        Builder setScopes(@Nullable OAuth2Scopes scopes);

        Builder setFlow(@Nullable String flow);

    }
    
    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of an OAuth2SecurityScheme.
     */
    @Immutable
    final class JsonFields {

        public static final JsonFieldDefinition<String> AUTHORIZATION = JsonFactory.newStringFieldDefinition(
                "authorization");

        public static final JsonFieldDefinition<String> TOKEN = JsonFactory.newStringFieldDefinition(
                "token");

        public static final JsonFieldDefinition<String> REFRESH = JsonFactory.newStringFieldDefinition(
                "refresh");

        public static final JsonFieldDefinition<String> SCOPES = JsonFactory.newStringFieldDefinition(
                "scopes");

        public static final JsonFieldDefinition<JsonArray> SCOPES_MULTIPLE = JsonFactory.newJsonArrayFieldDefinition(
                "scopes");

        public static final JsonFieldDefinition<String> FLOW = JsonFactory.newStringFieldDefinition(
                "flow");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
