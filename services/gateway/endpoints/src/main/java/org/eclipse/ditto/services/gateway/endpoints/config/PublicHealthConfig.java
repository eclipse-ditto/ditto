/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.gateway.endpoints.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for the public health endpoint of the Ditto Gateway service.
 * <p>
 * Java serialization is supported for {@code CachesConfig}.
 * </p>
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
