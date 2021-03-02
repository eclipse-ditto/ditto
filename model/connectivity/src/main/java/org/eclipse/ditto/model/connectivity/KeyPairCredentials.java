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
package org.eclipse.ditto.model.connectivity;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Key pair credentials. Containing the public and the private key.
 */
@Immutable
public final class KeyPairCredentials implements Credentials {

    /**
     * Credential type name.
     */
    public static final String TYPE = "key-pair";

    private final String publicKey;
    private final String privateKey;

    private KeyPairCredentials(final String publicKey,
            final String privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    @Override
    public <T> T accept(final CredentialsVisitor<T> visitor) {
        return visitor.keyPair(this);
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
        if (o instanceof KeyPairCredentials) {
            final KeyPairCredentials that = (KeyPairCredentials) o;
            return Objects.equals(publicKey, that.publicKey) &&
                    Objects.equals(privateKey, that.privateKey);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicKey, privateKey);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "hashCode=" + hashCode() +
                "]";
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        jsonObjectBuilder.set(JsonFields.TYPE, TYPE);
        jsonObjectBuilder.set(JsonFields.PUBLIC_KEY, publicKey);
        jsonObjectBuilder.set(JsonFields.PRIVATE_KEY, privateKey);
        return jsonObjectBuilder.build();
    }

    static KeyPairCredentials fromJson(final JsonObject jsonObject) {
        final String publicKey = jsonObject.getValueOrThrow(JsonFields.PUBLIC_KEY);
        final String privateKey = jsonObject.getValueOrThrow(JsonFields.PRIVATE_KEY);
        return new KeyPairCredentials(publicKey, privateKey);
    }

    /**
     * Create a new builder initialized with fields of this object.
     *
     * @return a new builder.
     */
    public Builder toBuilder() {
        return new Builder(publicKey, privateKey);
    }

    /**
     * Create an builder.
     *
     * @return a new builder.
     */
    public static Builder newBuilder(final String publicKey, final String privateKey) {
        return new Builder(publicKey, privateKey);
    }

    /**
     * Builder of {@code KeyPair}.
     */
    public static final class Builder {

        private String publicKey;
        private String privateKey;

        private Builder(final String publicKey, final String privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }

        /**
         * Set the public key.
         *
         * @param publicKey the public key
         * @return this builder
         */
        public Builder publicKey(final String publicKey) {
            this.publicKey = publicKey;
            return this;
        }

        /**
         * Set the private key.
         *
         * @param privateKey the client key
         * @return this builder
         */
        public Builder privateKey(final String privateKey) {
            this.privateKey = privateKey;
            return this;
        }

        /**
         * Build a new KeyPair credentials.
         *
         * @return the credentials.
         */
        public KeyPairCredentials build() {
            return new KeyPairCredentials(publicKey, privateKey);
        }
    }

    /**
     * JSON field definitions.
     */
    public static final class JsonFields extends Credentials.JsonFields {

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
