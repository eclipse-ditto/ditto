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

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import java.util.List;

/**
 * Provides configuration settings for encrypting json field values in Connections.
 */
public interface FieldsEncryptionConfig {


    /**
     * Indicates whether encryption of connection fields should be enabled.
     *
     * @return {@code true} if connection fields encryption should be enabled.
     */
    boolean isEncryptionEnabled();


    /**
     * Returns the symmetricalKey used for encryption.
     * @return the symmetricalKey
     */
    String getSymmetricalKey();


    /**
     * Returns string json pointers to the values of json fields to be encrypted.
     * "uri" has a special handling in which only the password of the uri is encrypted.
     *
     * @return pointers list
     */
    List<String> getJsonPointers();



    /**
     * An enumeration of the known config path expressions and their associated default values for {@code FieldsEncryptionConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Determines whether json value encryption is enabled.
         */
        ENCRYPTION_ENABLED("encryption-enabled", false),
        /**
         * The symmetrical key used for encryption.
         */
        SYMMETRICAL_KEY("symmetrical-key", ""),

        /**
         * The pointer to the json values to be encrypted.
         */
        JSON_POINTERS("json-pointers", List.of(
                "/uri",
                "/credentials/key",
                "/sshTunnel/credentials/password",
                "/sshTunnel/credentials/privateKey",
                "/credentials/parameters/accessKey",
                "/credentials/parameters/secretKey",
                "/credentials/parameters/sharedKey",
                "/credentials/clientSecret"));

        private final String configPath;
        private final Object defaultValue;

        ConfigValue(final String theConfigPath, final Object theDefaultValue) {
            configPath = theConfigPath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return configPath;
        }
    }
}
