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
package org.eclipse.ditto.gateway.service.util.config.endpoints;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for the public health endpoint of the Ditto Gateway service.
 */
@Immutable
public interface PublicHealthConfig {

    /**
     * Returns the timeout for the cache of the external health check information.
     *
     * @return the timeout.
     */
    Duration getCacheTimeout();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code PublicHealthConfig}.
     */
    enum PublicHealthConfigValue implements KnownConfigValue {

        /**
         * The timeout for the cache of the external health check information.
         */
        CACHE_TIMEOUT("cache-timeout", Duration.ofSeconds(20L));

        private final String path;
        private final Object defaultValue;

        private PublicHealthConfigValue(final String thePath, final Object theDefaultValue) {
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
