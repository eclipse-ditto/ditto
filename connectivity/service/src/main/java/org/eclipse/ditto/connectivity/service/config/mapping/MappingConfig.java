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
package org.eclipse.ditto.connectivity.service.config.mapping;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.service.config.javascript.JavaScriptConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for Connectivity service's message mapping behaviour.
 */
@Immutable
public interface MappingConfig {

    /**
     * Returns the buffer size used for the queue in the message mapping processor actor responsible for doing the
     * enrichment.
     *
     * @return the buffer size.
     */
    int getBufferSize();

    /**
     * Returns the parallelism used for enriching messages in parallel in message mapping processor actor.
     *
     * @return the parallelism.
     */
    int getParallelism();

    /**
     * Returns the max pool size (parallelism) for mapping inbound and outbound messages. This limits the user-settable
     * configuration {@link org.eclipse.ditto.connectivity.model.Connection#getProcessorPoolSize()}.
     *
     * @return the max pool size.
     */
    int getMaxPoolSize();

    /**
     * Returns the config of the JavaScript message mapping.
     *
     * @return the config.
     */
    JavaScriptConfig getJavaScriptConfig();

    /**
     * Returns the config of mapper-limits
     *
     * @return the config.
     */
    MapperLimitsConfig getMapperLimitsConfig();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code MappingConfig}.
     */
    enum MappingConfigValue implements KnownConfigValue {

        /**
         * The buffer size used for the queue in the message mapping processor actor.
         */
        BUFFER_SIZE("buffer-size", 500),

        /**
         * The parallelism used for enriching messages in parallel in message mapping processor actor.
         */
        PARALLELISM("parallelism", 64),

        /**
         * The maximum parallelism used for mapping inbound and outbound messages in mapping processor actor.
         */
        MAX_POOL_SIZE("max-pool-size", 5);

        private final String path;
        private final Object defaultValue;

        MappingConfigValue(final String thePath, final Object theDefaultValue) {
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
