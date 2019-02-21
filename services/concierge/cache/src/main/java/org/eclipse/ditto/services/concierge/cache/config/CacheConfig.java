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

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of a particular cache of Concierge.
 * <p>
 * Java serialization is supported for {@code CacheConfig}.
 * </p>
 */
@Immutable
public interface CacheConfig {

    /**
     * Returns the maximum size of a cache.
     *
     * @return the maximum size.
     */
    long getMaximumSize();

    /**
     * Returns duration after which a cache entry expires.
     *
     * @return the duration between write and expiration.
     */
    Duration getExpireAfterWrite();

    /**
     * An enumeration of the known config path expressions and their associated default values for {@code CacheConfig}.
     */
    enum CacheConfigValue implements KnownConfigValue {

        /**
         * The maximum cache size.
         */
        MAXIMUM_SIZE("maximum-size", 50_000L),

        /**
         * Duration after which a cache entry expires.
         */
        EXPIRE_AFTER_WRITE("expire-after-write", Duration.ofMinutes(15L));

        private final String path;
        private final Object defaultValue;

        private CacheConfigValue(final String thePath, final Object theDefaultValue) {
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
