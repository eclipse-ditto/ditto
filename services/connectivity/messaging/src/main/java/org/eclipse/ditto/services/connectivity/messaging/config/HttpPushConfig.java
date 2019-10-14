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
package org.eclipse.ditto.services.connectivity.messaging.config;

import java.util.Collection;

import org.eclipse.ditto.services.base.config.http.HttpProxyConfig;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of the http-push connection type.
 */
public interface HttpPushConfig {

    /**
     * @return maximum number of concurrent requests per connection.
     */
    int getMaxParallelism();

    /**
     * @return maximum number of messages buffered at the publisher actor before dropping them.
     */
    int getMaxQueueSize();

    /**
     * @return configuration of the proxy for all outgoing HTTP requests.
     */
    HttpProxyConfig getHttpProxyConfig();

    /**
     * @return the list of blacklisted HTTP hostnames to which sending out data will be prevented.
     */
    Collection<String> getBlacklistedHostnames();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code HttpPushConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * How many parallel requests per connection to permit. Each takes up one outgoing TCP connection.
         */
        MAX_PARALLELISM("max-parallelism", 1),

        /**
         * How many messages to buffer in the publisher actor before dropping them. Each takes up to 100 KB heap space.
         */
        MAX_QUEUE_SIZE("max-queue-size", 10),

        /**
         * A comma separated list of blacklisted hostnames to which not http requests will be send out.
         */
        BLACKLISTED_HOSTNAMES("blacklisted-hostnames", "");

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
