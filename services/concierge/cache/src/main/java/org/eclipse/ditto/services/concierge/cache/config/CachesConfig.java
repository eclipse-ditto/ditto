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
package org.eclipse.ditto.services.concierge.cache.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.cache.config.CacheConfig;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of the caches of Concierge.
 * <p>
 * Java serialization is supported for {@code CachesConfig}.
 * </p>
 */
@Immutable
public interface CachesConfig {

    /**
     * Returns the duration to wait for entity shard regions.
     *
     * @return the internal ask timeout duration.
     */
    Duration getAskTimeout();

    /**
     * Returns the config of the ID cache.
     *
     * @return the config.
     */
    CacheConfig getIdCacheConfig();

    /**
     * Returns the config of the enforcer cache.
     *
     * @return the config.
     */
    CacheConfig getEnforcerCacheConfig();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code CachesConfig}.
     */
    enum CachesConfigValue implements KnownConfigValue {

        /**
         * The duration to wait for entity shard regions.
         */
        ASK_TIMEOUT("ask-timeout", Duration.ofSeconds(10L));

        private final String path;
        private final Object defaultValue;

        private CachesConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

    }

}
