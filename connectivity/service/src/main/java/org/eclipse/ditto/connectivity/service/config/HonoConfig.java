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
package org.eclipse.ditto.connectivity.service.config;

import java.net.URI;
import java.util.Set;

import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * This interface provides access to the configuration properties Hono connections.
 * The actual configuration can be obtained via actor system extension.
 */
public interface HonoConfig {

    /**
     * Prefix in .conf files
     */
    String PREFIX = "ditto.connectivity.hono";

    /**
     * Gets the Base URI configuration value.
     *
     * @return the connection base URI.
     */
    URI getBaseUri();

    /**
     * Indicates whether the certificates should be validated.
     *
     * @return {@code true} if the certificates should be validated, {@code false} else.
     */
    boolean isValidateCertificates();

    /**
     * Gets the SASL mechanism of Hono-connection (Kafka specific property).
     *
     * @return the configured SaslMechanism.
     */
    SaslMechanism getSaslMechanism();

    /**
     * Returns the URIs of bootstrap servers.
     *
     * @return an unmodifiable unsorted Set containing the URIs of bootstrap servers.
     */
    Set<URI> getBootstrapServerUris();

    /**
     * Gets the credentials for the specified Hono connection.
     *
     * @return the credentials of the connection.
     * @throws NullPointerException if {@code connectionId} is {@code null}.
     */
    UserPasswordCredentials getUserPasswordCredentials();

    enum HonoConfigValue implements KnownConfigValue {

        /**
         * Base URL, including port number.
         */
        BASE_URI("base-uri", "tcp://localhost:30092"),

        /**
         * validateCertificates boolean property.
         */
        VALIDATE_CERTIFICATES("validate-certificates", false),

        /**
         * SASL mechanism for connections of type Hono.
         */
        SASL_MECHANISM("sasl-mechanism", SaslMechanism.PLAIN.name()),

        /**
         * Bootstrap servers, comma separated.
         */
        BOOTSTRAP_SERVERS("bootstrap-servers", "bootstrap.server:9999"),

        /**
         * The Hono credentials username.
         */
        USERNAME("username", ""),

        /**
         * The Hono credentials password.
         */
        PASSWORD("password", "");

        private final String path;
        private final Object defaultValue;

        HonoConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }

    enum SaslMechanism {

        PLAIN("plain"),

        SCRAM_SHA_256("scram-sha-256"),

        SCRAM_SHA_512("scram-sha-512");

        private final String value;

        SaslMechanism(final String value) {
            this.value = value;
        }

        /**
         * Returns the value of this SaslMechanism.
         *
         * @return the value.
         */
        @Override
        public String toString() {
            return value;
        }

    }

}