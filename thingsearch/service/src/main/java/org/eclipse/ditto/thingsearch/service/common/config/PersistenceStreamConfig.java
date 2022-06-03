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
package org.eclipse.ditto.thingsearch.service.common.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import com.mongodb.WriteConcern;

/**
 * Provides configuration settings of the persistence stream.
 */
@Immutable
public interface PersistenceStreamConfig extends StreamStageConfig {

    /**
     * Returns the delay between DB acknowledgement and sending "search-persisted" acknowledgement.
     *
     * @return the delay.
     */
    Duration getAckDelay();

    /**
     * Returns the MongoDB {@link com.mongodb.WriteConcern} to use for updating search index for events which required
     * {@code "search-persisted"} Acknowledgements.
     *
     * @return the write concern to use for search index updates requiring acknowledgement.
     */
    WriteConcern getWithAcknowledgementsWriteConcern();

    /**
     * An enumeration of known config path expressions and their associated default values for
     * {@code PersistenceStreamConfig}.
     * This enumeration is a logical extension of {@link StreamStageConfig.StreamStageConfigValue}.
     */
    enum PersistenceStreamConfigValue implements KnownConfigValue {

        /**
         * Internal delay between acknowledgement from database and the sending of "search-persisted" acknowledgements.
         */
        ACK_DELAY("ack-delay", Duration.ZERO),

        /**
         * The write concern used for search index updates requiring acknowledgements.
         * See {@link com.mongodb.WriteConcern} for available options.
         */
        WITH_ACKS_WRITE_CONCERN("with-acks-writeConcern", "journaled");

        private final String configPath;
        private final Object defaultValue;

        PersistenceStreamConfigValue(final String configPath, final Object defaultValue) {
            this.configPath = configPath;
            this.defaultValue = defaultValue;
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
