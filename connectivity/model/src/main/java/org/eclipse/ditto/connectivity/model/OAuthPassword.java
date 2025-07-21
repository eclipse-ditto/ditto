/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
 * Credentials used to retrieve a new access token using "password" flow with the given
 * client id, secret and scope.
 *
 * @since 3.8.0
 */
@Immutable
public final class OAuthPassword implements OAuthCredentials {

    /**
     * Credential type name.
     */
    public static final String TYPE = "oauth-password";

    private final String tokenEndpoint;
    private final String clientId;
    @Nullable private final String clientSecret;
    private final String requestedScopes;
    @Nullable private final String audience;
    private final String username;
    private final String password;

    private OAuthPassword(final String tokenEndpoint,
            final String clientId,
            @Nullable final String clientSecret,
            final String requestedScopes,
            @Nullable final String audience,
            @Nullable final String username,
            @Nullable final String password
    ) {
        this.tokenEndpoint = checkNotNull(tokenEndpoint, "tokenEndpoint");
        this.clientId = checkNotNull(clientId, "clientId");
        this.clientSecret = clientSecret; // clientSecret might be null in case of a public client
        this.requestedScopes = checkNotNull(requestedScopes, "requestedScopes");
        this.audience = audience;
        this.username = checkNotNull(username, "username");
        this.password = checkNotNull(password, "password");
    }

    @Override
    public <T> T accept(final CredentialsVisitor<T> visitor) {
        return visitor.oauthPassword(this);
    }

    @Override
    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public Optional<String> getClientSecret() {
        return Optional.ofNullable(clientSecret);
    }

    @Override
    public String getRequestedScopes() {
        return requestedScopes;
    }

    @Override
    public Optional<String> getAudience() {
        return Optional.ofNullable(audience);
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final OAuthPassword that = (OAuthPassword) o;
        return Objects.equals(tokenEndpoint, that.tokenEndpoint) &&
                Objects.equals(clientId, that.clientId) &&
                Objects.equals(clientSecret, that.clientSecret) &&
                Objects.equals(requestedScopes, that.requestedScopes) &&
                Objects.equals(audience, that.audience) &&
                Objects.equals(username, that.username) &&
                Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokenEndpoint, clientId, clientSecret, requestedScopes, audience, username, password);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "tokenEndpoint=" + tokenEndpoint +
                ", clientId=" + clientId +
                ", clientSecret=" + clientSecret +
                ", requestedScopes=" + requestedScopes +
                ", audience=" + audience +
                ", username=" + username +
                ", password=***" +
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
        jsonObjectBuilder.set(JsonFields.AUDIENCE, audience, Objects::nonNull);
        jsonObjectBuilder.set(JsonFields.USERNAME, username, Objects::nonNull);
        jsonObjectBuilder.set(JsonFields.PASSWORD, password, Objects::nonNull);
        return jsonObjectBuilder.build();
    }

    static OAuthPassword fromJson(final JsonObject jsonObject) {
        final Builder builder = newBuilder();
        jsonObject.getValue(JsonFields.TOKEN_ENDPOINT).ifPresent(builder::tokenEndpoint);
        jsonObject.getValue(JsonFields.CLIENT_ID).ifPresent(builder::clientId);
        jsonObject.getValue(JsonFields.CLIENT_SECRET).ifPresent(builder::clientSecret);
        jsonObject.getValue(JsonFields.REQUESTED_SCOPES).ifPresent(builder::scope);
        jsonObject.getValue(JsonFields.AUDIENCE).ifPresent(builder::audience);
        jsonObject.getValue(JsonFields.USERNAME).ifPresent(builder::username);
        jsonObject.getValue(JsonFields.PASSWORD).ifPresent(builder::password);
        return builder.build();
    }

    /**
     * Create empty credentials.
     *
     * @return empty credentials.
     */
    public static OAuthPassword empty() {
        return newBuilder().build();
    }

    /**
     * Create a new builder initialized with fields of this object.
     *
     * @return a new builder.
     */
    public Builder toBuilder() {
        return new Builder().clientId(clientId).clientSecret(clientSecret).tokenEndpoint(tokenEndpoint)
                .scope(requestedScopes).audience(audience).username(username).password(password);
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
     * Builder of {@code OAuthPassword}.
     */
    public static final class Builder {

        @Nullable private String tokenEndpoint;
        @Nullable private String clientId;
        @Nullable private String clientSecret;
        @Nullable private String scope;
        @Nullable private String audience;
        @Nullable private String username;
        @Nullable private String password;

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
        public Builder clientSecret(@Nullable final String clientSecret) {
            this.clientSecret = clientSecret;
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
         * @param username the username
         * @return this builder
         */
        public Builder username(final String username) {
            this.username = checkNotNull(username, "username");
            return this;
        }

        /**
         * @param password the password
         * @return this builder
         */
        public Builder password(final String password) {
            this.password = checkNotNull(password, "password");
            return this;
        }

        /**
         * Build a new {@code OAuthPassword}.
         *
         * @return the credentials.
         */
        public OAuthPassword build() {
            return new OAuthPassword(tokenEndpoint, clientId, clientSecret, scope, audience, username, password);
        }
    }

    /**
     * JSON field definitions.
     */
    public static final class JsonFields extends OAuthCredentials.JsonFields {

        /**
         * JSON field definition of the username.
         */
        public static final JsonFieldDefinition<String> USERNAME = JsonFieldDefinition.ofString(
                "username");

        /**
         * JSON field definition of the password.
         */
        public static final JsonFieldDefinition<String> PASSWORD = JsonFieldDefinition.ofString(
                "password");
    }
}
