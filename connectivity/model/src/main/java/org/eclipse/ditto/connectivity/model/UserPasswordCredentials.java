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

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * User and password credentials.
 *
 * @since 2.0.0
 */
@Immutable
public final class UserPasswordCredentials implements Credentials {

    /**
     * Credential type name.
     */
    public static final String TYPE = "plain";

    private final String username;
    private final String password;

    private UserPasswordCredentials(final String username,
            final String password) {

        this.username = username;
        this.password = password;
    }

    @Override
    public <T> T accept(final CredentialsVisitor<T> visitor) {
        return visitor.usernamePassword(this);
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
        final UserPasswordCredentials that = (UserPasswordCredentials) o;
        return username.equals(that.username) && password.equals(that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, password);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "username=" + username +
                ", password=***" + // password omitted intentionally
                "]";
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        jsonObjectBuilder.set(Credentials.JsonFields.TYPE, TYPE);
        jsonObjectBuilder.set(JsonFields.USERNAME, username);
        jsonObjectBuilder.set(JsonFields.PASSWORD, password);
        return jsonObjectBuilder.build();
    }

    static UserPasswordCredentials fromJson(final JsonObject jsonObject) {
        final String username = jsonObject.getValueOrThrow(JsonFields.USERNAME);
        final String password = jsonObject.getValueOrThrow(JsonFields.PASSWORD);
        return new UserPasswordCredentials(username, password);
    }

    /**
     * Create credentials with username and password.
     *
     * @param username the username
     * @param password the password
     * @return credentials.
     */
    public static UserPasswordCredentials newInstance(final String username, final String password) {
        return new UserPasswordCredentials(username, password);
    }

    /**
     * Create credentials from JsonObject
     *
     * @param jsonObject the jsonObject
     * @return credentials.
     * @since 3.2.0
     */
    public static UserPasswordCredentials newInstance(final JsonObject jsonObject) {
        return UserPasswordCredentials.fromJson(jsonObject);
    }

    /**
     * JSON field definitions.
     */
    public static final class JsonFields extends Credentials.JsonFields {

        /**
         * JSON field containing the username
         */
        public static final JsonFieldDefinition<String> USERNAME = JsonFieldDefinition.ofString("username");

        /**
         * JSON field containing the password
         */
        public static final JsonFieldDefinition<String> PASSWORD = JsonFieldDefinition.ofString("password");
    }
}
