/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of the AMQP 0.9.1 protocol.
 */
@Immutable
public interface Amqp091Config {

    /**
     * Returns how long to wait for broker acknowledgement for a published AMQP 0.9.1 message
     * in order to translate it into a Ditto Acknowledgement.
     *
     * @return the publisher
     */
    Duration getPublisherPendingAckTTL();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code Amqp091Config}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * How long to wait for broker acknowledgement for published messages in order to translate it into
         * a Ditto Acknowledgement.
         */
        PUBLISHER_PENDING_ACK_TTL("publisher.pending-ack-ttl", Duration.ofMinutes(1L));

        private final String path;
        private final Object defaultValue;

        ConfigValue(final String thePath, final Object theDefaultValue) {
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

}
