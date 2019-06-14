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
package org.eclipse.ditto.services.utils.cache.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of a particular cache of Concierge.
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
     * Returns duration after which a written cache entry expires.
     *
     * @return the duration between write and expiration.
     */
    Duration getExpireAfterWrite();

    /**
     * Returns the duration after which an accessed cache entry expires.
     *
     * @return the duration between last access and expiration.
     */
    Duration getExpireAfterAccess();

    /**
     * An enumeration of the known config path expressions and their associated default values for {@code CacheConfig}.
     */
    enum CacheConfigValue implements KnownConfigValue {

        /**
         * The maximum cache size.
         */
        MAXIMUM_SIZE("maximum-size", 50_000L),

        /**
         * Duration after which a written cache entry expires.
         */
        EXPIRE_AFTER_WRITE("expire-after-write", Duration.ofMinutes(15L)),

        /**
         * Duration after which an accessed cache entry expires.
         */
        EXPIRE_AFTER_ACCESS("expire-after-access", Duration.ofMinutes(15L));

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
