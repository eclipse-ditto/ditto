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

import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

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
     * Returns the current symmetrical key used for encryption.
     * This is THE key used for encrypting new data.
     *
     * @return the current symmetrical key
     */
    Optional<String> getSymmetricalKey();

    /**
     * Returns the old symmetrical key used for decryption fallback during key rotation.
     * When set, the system will try to decrypt with the current key first, and fallback to this key if decryption fails.
     * <p>
     * Typical usage during key rotation:
     * <ol>
     *   <li>Move current key to old-symmetrical-key</li>
     *   <li>Set new key as symmetrical-key</li>
     *   <li>Trigger migration via DevOps command</li>
     *   <li>Remove old-symmetrical-key after migration completes</li>
     * </ol>
     *
     * @return the old symmetrical key, empty if not configured
     */
    Optional<String> getOldSymmetricalKey();


    /**
     * Returns string json pointers to the values of json fields to be encrypted.
     * "uri" has a special handling in which only the password of the uri is encrypted.
     *
     * @return pointers list
     */
    List<String> getJsonPointers();

    /**
     * Returns the batch size for the encryption migration process.
     *
     * @return the batch size
     */
    int getMigrationBatchSize();

    /**
     * Returns the maximum number of documents to migrate per minute.
     * This throttles the migration stream to avoid overwhelming the database.
     *
     * @return the maximum documents per minute, 0 means no throttling
     */
    int getMigrationMaxDocumentsPerMinute();

    /**
     * An enumeration of the known config path expressions and their associated default values for {@code FieldsEncryptionConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Determines whether json value encryption is enabled.
         */
        ENCRYPTION_ENABLED("encryption-enabled", false),

        /**
         * The current symmetrical key used for encryption.
         * This is THE key used for encrypting all new data.
         */
        SYMMETRICAL_KEY("symmetrical-key", ""),

        /**
         * The old symmetrical key used for decryption fallback during key rotation.
         * When set, the system will attempt to decrypt with symmetrical-key first,
         * and fallback to this key if decryption fails.
         */
        OLD_SYMMETRICAL_KEY("old-symmetrical-key", ""),

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
                "/credentials/clientSecret",
                "/credentials/password"
        )),

        /**
         * The batch size for the encryption migration process.
         */
        MIGRATION_BATCH_SIZE("migration.batch-size", 100),

        /**
         * The maximum number of documents to migrate per minute.
         * This throttles the migration stream to avoid overwhelming the database.
         * 0 means no throttling.
         */
        MIGRATION_MAX_DOCUMENTS_PER_MINUTE("migration.max-documents-per-minute", 200);

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
