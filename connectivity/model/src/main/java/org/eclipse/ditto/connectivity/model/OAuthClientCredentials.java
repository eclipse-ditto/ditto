/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Credentials used to retrieve a new access token using client credentials flow with the given
 * client id, secret and scope.
 *
 * @since 2.2.0
 */
@Immutable
public final class OAuthClientCredentials implements Credentials {

    /**
     * Credential type name.
     */
    public static final String TYPE = "oauth-client-credentials";

    private final String tokenEndpoint;
    private final String clientId;
    private final String clientSecret;
    private final String requestedScopes;
    @Nullable private final String audience;

    private OAuthClientCredentials(final String tokenEndpoint, final String clientId, final String clientSecret,
                                   final String requestedScopes, @Nullable final String audience) {
        this.tokenEndpoint = checkNotNull(tokenEndpoint, "tokenEndpoint");
        this.clientId = checkNotNull(clientId, "clientId");
        this.clientSecret = checkNotNull(clientSecret, "clientSecret");
        this.requestedScopes = checkNotNull(requestedScopes, "requestedScopes");
        this.audience = audience;
    }

    @Override
    public <T> T accept(final CredentialsVisitor<T> visitor) {
        return visitor.oauthClientCredentials(this);
    }

    /**
     * @return the token endpoint
     */
    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    /**
     * @return the client id
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * @return the client secret
     */
    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * @return the scope
     */
    public String getRequestedScopes() {
        return requestedScopes;
    }

    /**
     * @return the audience
     */
    public Optional<String> getAudience() {
        return Optional.ofNullable(audience);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final OAuthClientCredentials that = (OAuthClientCredentials) o;
        return tokenEndpoint.equals(that.tokenEndpoint) && clientId.equals(that.clientId) &&
                clientSecret.equals(that.clientSecret) && requestedScopes.equals(that.requestedScopes) &&
                Objects.equals(audience, that.audience);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokenEndpoint, clientId, clientSecret, requestedScopes, audience);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "tokenEndpoint=" + tokenEndpoint +
                ", clientId=" + clientId +
                ", clientSecret=" + clientSecret +
                ", requestedScopes=" + requestedScopes +
                ", audience=" + audience +
                "]";
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        jsonObjectBuilder.set(Credentials.JsonFields.TYPE, TYPE);
        jsonObjectBuilder.set(JsonFields.TOKEN_ENDPOINT, tokenEndpoint, Objects::nonNull);
        jsonObjectBuilder.set(JsonFields.CLIENT_ID, clientId, Objects::nonNull);
        jsonObjectBuilder.set(JsonFields.CLIENT_SECRET, clientSecret, Objects::nonNull);
        jsonObjectBuilder.set(JsonFields.REQUESTED_SCOPES, requestedScopes, Objects::nonNull);
        jsonObjectBuilder.set(JsonFields.AUDIENCE, audience);
        return jsonObjectBuilder.build();
    }

    static OAuthClientCredentials fromJson(final JsonObject jsonObject) {
        final Builder builder = newBuilder();
        jsonObject.getValue(JsonFields.TOKEN_ENDPOINT).ifPresent(builder::tokenEndpoint);
        jsonObject.getValue(JsonFields.CLIENT_ID).ifPresent(builder::clientId);
        jsonObject.getValue(JsonFields.CLIENT_SECRET).ifPresent(builder::clientSecret);
        jsonObject.getValue(JsonFields.REQUESTED_SCOPES).ifPresent(builder::scope);
        jsonObject.getValue(JsonFields.AUDIENCE).ifPresent(builder::audience);
        return builder.build();
    }

    /**
     * Create empty credentials with no certificates.
     *
     * @return empty credentials.
     */
    public static OAuthClientCredentials empty() {
        return newBuilder().build();
    }

    /**
     * Create a new builder initialized with fields of this object.
     *
     * @return a new builder.
     */
    public Builder toBuilder() {
        return new Builder().clientId(clientId).clientSecret(clientSecret).tokenEndpoint(tokenEndpoint).scope(
                requestedScopes).audience(audience);
    }

    /**
     * Create an empty builder.
     *
     * @return a new builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder of {@code X509Credentials}.
     */
    public static final class Builder {

        private String tokenEndpoint;
        private String clientId;
        private String clientSecret;
        private String scope;
        @Nullable private String audience;

        /**
         * @param tokenEndpoint the token endpoint
         * @return this builder
         */
        public Builder tokenEndpoint(final String tokenEndpoint) {
            this.tokenEndpoint = checkNotNull(tokenEndpoint, "tokenEndpoint");
            return this;
        }

        /**
         * @param clientId the clientId
         * @return this builder
         */
        public Builder clientId(final String clientId) {
            this.clientId = checkNotNull(clientId, "clientId");
            return this;
        }

        /**
         * @param clientSecret the clientSecret
         * @return this builder
         */
        public Builder clientSecret(final String clientSecret) {
            this.clientSecret = checkNotNull(clientSecret, "clientSecret");
            return this;
        }

        /**
         * @param scope the scope
         * @return this builder
         */
        public Builder scope(final String scope) {
            this.scope = checkNotNull(scope, "scope");
            return this;
        }

        /**
         * @param audience the audience
         * @return this builder
         */
        public Builder audience(@Nullable final String audience) {
            this.audience = audience;
            return this;
        }

        /**
         * Build a new {@code OAuthClientCredentials}.
         *
         * @return the credentials.
         */
        public OAuthClientCredentials build() {
            return new OAuthClientCredentials(tokenEndpoint, clientId, clientSecret, scope, audience);
        }
    }

    /**
     * JSON field definitions.
     */
    public static final class JsonFields extends Credentials.JsonFields {

        /**
         * JSON field definition of OAuth token endpoint.
         */
        public static final JsonFieldDefinition<String> TOKEN_ENDPOINT = JsonFieldDefinition.ofString("tokenEndpoint");

        /**
         * JSON field definition of client ID.
         */
        public static final JsonFieldDefinition<String> CLIENT_ID = JsonFieldDefinition.ofString("clientId");

        /**
         * JSON field definition of client secret.
         */
        public static final JsonFieldDefinition<String> CLIENT_SECRET = JsonFieldDefinition.ofString("clientSecret");

        /**
         * JSON field definition of the requested scopes.
         */
        public static final JsonFieldDefinition<String> REQUESTED_SCOPES = JsonFieldDefinition.ofString(
                "requestedScopes");

        /**
         * JSON field definition of the audience.
         */
        public static final JsonFieldDefinition<String> AUDIENCE = JsonFieldDefinition.ofString(
                "audience");
    }
}
