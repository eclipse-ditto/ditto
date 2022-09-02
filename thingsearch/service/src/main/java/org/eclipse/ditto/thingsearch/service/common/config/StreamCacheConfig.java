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

import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of the streaming cache of the Search service.
 */
@Immutable
public interface StreamCacheConfig extends CacheConfig {

    /**
     * Returns the name of the dispatcher to run async cache loaders which do not block threads.
     *
     * @return the name.
     */
    String getDispatcherName();

    /**
     * Returns the delay before retrying a cache query if the cached value is out of date.
     *
     * @return the delay.
     */
    Duration getRetryDelay();

    /**
     * An enumeration of known config path expressions and their associated default values for
     * {@code StreamCacheConfig}.
     * This enumeration is a logical extension of {@link CacheConfigValue}.
     */
    enum StreamCacheConfigValue implements KnownConfigValue {

        /**
         * The name of the dispatcher to run async cache loaders which do not block threads.
         */
        DISPATCHER_NAME("dispatcher", "akka.actor.default-dispatcher"),

        /**
         * The delay before retrying a cache query if the cached value is out of date.
         */
        RETRY_DELAY("retry-delay", Duration.ofSeconds(1L));

        private final String configPath;
        private final Object defaultValue;

        StreamCacheConfigValue(final String configPath, final Object defaultValue) {
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
