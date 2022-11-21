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
package org.eclipse.ditto.base.service.config.http;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides the configuration settings of the Ditto HTTP endpoint.
 */
@Immutable
public interface HttpConfig {

    /**
     * Returns the hostname value of the HTTP endpoint.
     *
     * @return an Optional containing the hostname or an empty Optional if no host name was configured.
     */
    String getHostname();

    /**
     * Returns the port number of the HTTP endpoint.
     *
     * @return an Optional containing the port number or an empty Optional if no port number was configured.
     */
    int getPort();

    /**
     * Timeout after which all requests and connections shall be forcefully terminated during coordinated shutdown.
     *
     * @return the timeout
     */
    Duration getCoordinatedShutdownTimeout();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code HttpConfig}.
     */
    enum HttpConfigValue implements KnownConfigValue {

        /**
         * The hostname value of the HTTP endpoint.
         */
        HOSTNAME("hostname", ""),

        /**
         * The port number of the HTTP endpoint.
         */
        PORT("port", 8080),

        COORDINATED_SHUTDOWN_TIMEOUT("coordinated-shutdown-timeout", Duration.ofSeconds(65));

        private final String path;
        private final Object defaultValue;

        private HttpConfigValue(final String thePath, final Object theDefaultValue) {
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
