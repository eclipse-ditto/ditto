/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * X.509 credentials in PEM format. It includes an optional client keypair and zero or more trusted certificates
 * (i. e., {@code -CAfile} of OpenSSL s_client) with which to authenticate the server certificate.
 */
@Immutable
public final class ClientCertificateCredentials implements Credentials {

    /**
     * Credential type name.
     */
    public static final String TYPE = "client-cert";

    @Nullable
    private final String clientCertificate;

    @Nullable
    private final String clientKey;

    private ClientCertificateCredentials(@Nullable final String clientCertificate,
            @Nullable final String clientKey) {

        this.clientCertificate = clientCertificate;
        this.clientKey = clientKey;
    }

    @Override
    public <T> T accept(final CredentialsVisitor<T> visitor) {
        return visitor.clientCertificate(this);
    }

    /**
     * @return the client certificate
     */
    public Optional<String> getClientCertificate() {
        return Optional.ofNullable(clientCertificate);
    }

    /**
     * @return the client key
     */
    public Optional<String> getClientKey() {
        return Optional.ofNullable(clientKey);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ClientCertificateCredentials that = (ClientCertificateCredentials) o;
        return Objects.equals(clientCertificate, that.clientCertificate) &&
                Objects.equals(clientKey, that.clientKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientCertificate, clientKey);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "clientCertificate=" + clientCertificate +
                ", clientKey=***" + // clientKey omitted intentionally
                "]";
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        jsonObjectBuilder.set(Credentials.JsonFields.TYPE, TYPE);
        jsonObjectBuilder.set(JsonFields.CLIENT_CERTIFICATE, clientCertificate, Objects::nonNull);
        jsonObjectBuilder.set(JsonFields.CLIENT_KEY, clientKey, Objects::nonNull);
        return jsonObjectBuilder.build();
    }

    static ClientCertificateCredentials fromJson(final JsonObject jsonObject) {
        final Builder builder = newBuilder();
        jsonObject.getValue(JsonFields.CLIENT_CERTIFICATE).ifPresent(builder::clientCertificate);
        jsonObject.getValue(JsonFields.CLIENT_KEY).ifPresent(builder::clientKey);
        return builder.build();
    }

    /**
     * Create empty credentials with no certificates.
     *
     * @return empty credentials.
     */
    public static ClientCertificateCredentials empty() {
        return newBuilder().build();
    }

    /**
     * Create a new builder initialized with fields of this object.
     *
     * @return a new builder.
     */
    public Builder toBuilder() {
        return new Builder().clientCertificate(clientCertificate).clientKey(clientKey);
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

        @Nullable
        private String clientCertificate;

        @Nullable
        private String clientKey;

        /**
         * Set the client certificate.
         *
         * @param clientCertificate the client certificate
         * @return this builder
         */
        public Builder clientCertificate(@Nullable final String clientCertificate) {
            this.clientCertificate = clientCertificate;
            return this;
        }

        /**
         * Set the client key.
         *
         * @param clientKey the client key
         * @return this builder
         */
        public Builder clientKey(@Nullable final String clientKey) {
            this.clientKey = clientKey;
            return this;
        }

        /**
         * Build a new X.509 credentials.
         *
         * @return the credentials.
         */
        public ClientCertificateCredentials build() {
            return new ClientCertificateCredentials(clientCertificate, clientKey);
        }
    }

    /**
     * JSON field definitions.
     */
    public static final class JsonFields extends Credentials.JsonFields {

        /**
         * JSON field definition of client certificate identical to the corresponding command line argument of OpenSSL
         * s_client.
         */
        public static final JsonFieldDefinition<String> CLIENT_CERTIFICATE = JsonFieldDefinition.ofString("cert");

        /**
         * JSON field definition of client private key identical to the corresponding command line argument of
         * OpenSSL s_client.
         */
        public static final JsonFieldDefinition<String> CLIENT_KEY = JsonFieldDefinition.ofString("key");
    }
}
