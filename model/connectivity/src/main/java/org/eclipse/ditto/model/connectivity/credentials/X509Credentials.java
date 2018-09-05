/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.connectivity.credentials;

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
public final class X509Credentials implements Credentials {

    /**
     * Credential type name.
     */
    public static final String TYPE = "x.509/pem";

    @Nullable
    private final String clientCertificate;

    @Nullable
    private final String clientKey;

    @Nullable
    private final String trustedCertificates;

    private X509Credentials(@Nullable final String clientCertificate,
            @Nullable final String clientKey,
            @Nullable final String trustedCertificates) {
        this.clientCertificate = clientCertificate;
        this.clientKey = clientKey;
        this.trustedCertificates = trustedCertificates;
    }

    @Override
    public <T> T accept(final CredentialsVisitor<T> visitor) {
        return visitor.x509(this);
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

    /**
     * @return the CA certificate
     */
    public Optional<String> getTrustedCertificates() {
        return Optional.ofNullable(trustedCertificates);
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof X509Credentials) {
            final X509Credentials that = (X509Credentials) o;
            return Objects.equals(clientCertificate, that.clientCertificate) &&
                    Objects.equals(clientKey, that.clientKey) &&
                    Objects.equals(trustedCertificates, that.trustedCertificates);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientCertificate, clientKey, trustedCertificates);
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
        jsonObjectBuilder.set(JsonFields.CLIENT_CERTIFICATE, clientCertificate, Objects::nonNull);
        jsonObjectBuilder.set(JsonFields.CLIENT_KEY, clientKey, Objects::nonNull);
        jsonObjectBuilder.set(JsonFields.TRUSTED_CERTIFICATES, trustedCertificates, Objects::nonNull);
        return jsonObjectBuilder.build();
    }

    static X509Credentials fromJson(final JsonObject jsonObject) {
        final Builder builder = newBuilder();
        jsonObject.getValue(JsonFields.CLIENT_CERTIFICATE).ifPresent(builder::clientCertificate);
        jsonObject.getValue(JsonFields.CLIENT_KEY).ifPresent(builder::clientKey);
        jsonObject.getValue(JsonFields.TRUSTED_CERTIFICATES).ifPresent(builder::trustedCertificates);
        return builder.build();
    }

    /**
     * Create a new builder initialized with fields of this object.
     *
     * @return a new builder.
     */
    public Builder toBuilder() {
        return new Builder().clientCertificate(clientCertificate)
                .clientKey(clientKey)
                .trustedCertificates(trustedCertificates);
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

        @Nullable
        private String trustedCertificates;

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
         * Set the trusted certificates.
         *
         * @param trustedCertificates the trusted certificates
         * @return this builder
         */
        public Builder trustedCertificates(@Nullable final String trustedCertificates) {
            this.trustedCertificates = trustedCertificates;
            return this;
        }

        /**
         * Build a new X.509 credentials.
         *
         * @return the credentials.
         */
        public X509Credentials build() {
            return new X509Credentials(clientCertificate, clientKey, trustedCertificates);
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

        /**
         * JSON field definition of trusted certificates.
         */
        public static final JsonFieldDefinition<String> TRUSTED_CERTIFICATES = JsonFieldDefinition.ofString("ca");
    }
}
