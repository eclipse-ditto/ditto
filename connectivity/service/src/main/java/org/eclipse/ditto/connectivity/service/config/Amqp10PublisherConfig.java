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
package org.eclipse.ditto.connectivity.service.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of the AMQP 1.0 publisher.
 */
@Immutable
public interface Amqp10PublisherConfig {

    /**
     * @return maximum number of messages buffered at the publisher actor before dropping them.
     */
    int getMaxQueueSize();

    /**
     * @return the number of possible parallel message publications per publisher actor.
     */
    int getParallelism();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code Amqp10PublisherConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * How many messages to buffer in the publisher actor before dropping them.
         */
        MAX_QUEUE_SIZE("max-queue-size", 1000),

        /**
         * How many messages will be published in parallel
         */
        PARALLELISM("parallelism", 3);

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
