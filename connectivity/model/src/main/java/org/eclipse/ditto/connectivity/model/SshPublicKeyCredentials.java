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
 * Holds information required to do SSH public key authentication, namely: {@code username}, {@code public key} and
 * {@code private key}.
 *
 * @since 2.0.0
 */
@Immutable
public final class SshPublicKeyCredentials implements Credentials {

    /**
     * Credential type name.
     */
    public static final String TYPE = "public-key";

    private final String username;
    private final String publicKey;
    private final String privateKey;

    private SshPublicKeyCredentials(final String username, final String publicKey, final String privateKey) {
        this.username = username;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    public static SshPublicKeyCredentials of(final String username, final String publicKey,
            final String privateKey) {
        return new SshPublicKeyCredentials(username, publicKey, privateKey);
    }

    @Override
    public <T> T accept(final CredentialsVisitor<T> visitor) {
        return visitor.sshPublicKeyAuthentication(this);
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return the public key
     */
    public String getPublicKey() {
        return publicKey;
    }

    /**
     * @return the private key
     */
    public String getPrivateKey() {
        return privateKey;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SshPublicKeyCredentials that = (SshPublicKeyCredentials) o;
        return username.equals(that.username) && publicKey.equals(that.publicKey) && privateKey.equals(that.privateKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, publicKey, privateKey);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "username=" + username +
                ", publicKey=" + publicKey +
                ", privateKey=***" + // private key omitted intentionally
                "]";
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        jsonObjectBuilder.set(Credentials.JsonFields.TYPE, TYPE);
        jsonObjectBuilder.set(JsonFields.USERNAME, username);
        jsonObjectBuilder.set(JsonFields.PUBLIC_KEY, publicKey);
        jsonObjectBuilder.set(JsonFields.PRIVATE_KEY, privateKey);
        return jsonObjectBuilder.build();
    }

    static SshPublicKeyCredentials fromJson(final JsonObject jsonObject) {
        final String username = jsonObject.getValueOrThrow(JsonFields.USERNAME);
        final String publicKey = jsonObject.getValueOrThrow(JsonFields.PUBLIC_KEY);
        final String privateKey = jsonObject.getValueOrThrow(JsonFields.PRIVATE_KEY);
        return new SshPublicKeyCredentials(username, publicKey, privateKey);
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
         * JSON field containing the public key
         */
        public static final JsonFieldDefinition<String> PUBLIC_KEY = JsonFieldDefinition.ofString("publicKey");

        /**
         * JSON field containing the private key
         */
        public static final JsonFieldDefinition<String> PRIVATE_KEY = JsonFieldDefinition.ofString("privateKey");
    }
}
