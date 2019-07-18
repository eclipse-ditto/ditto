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

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for Connectivity service's client.
 */
@Immutable
public interface ClientConfig {

    /**
     * Returns the duration after the init process is triggered (in case no connect command was received by the
     * client actor).
     *
     * @return the init timeout.
     */
    Duration getInitTimeout();

    /**
     * Initial timeout when connecting to a remote system. If the connection could not be established after this time, the
     * service will try to reconnect. If a failure happened during connecting, then the service will wait for at least this
     * time until it will try to reconnect. The max timeout is defined in {@link ClientConfig#getConnectingMaxTimeout()}.
     * @return the minimum connecting timeout.
     */
    Duration getConnectingMinTimeout();
    /**
     * Max timeout (until reconnecting) when connecting to a remote system. See docs on {@link ClientConfig#getConnectingMinTimeout()}
     * for more information on the concept.
     * @return the maximum connecting timeout.
     * @see ClientConfig#getConnectingMinTimeout()
     */
    Duration getConnectingMaxTimeout();

    /**
     * How long the service will wait for a successful connection when testing a new connection. If no response is
     * received after this duration, the test will be assumed a failure.
     * @return the testing timeout.
     */
    Duration getTestingTimeout();

    /**
     * An enumeration of the known config path expressions and their associated default values for {@code ClientConfig}.
     */
    enum ClientConfigValue implements KnownConfigValue {

        /**
         * The duration after the init process is triggered.
         */
        INIT_TIMEOUT("init-timeout", Duration.ofSeconds(5L)),

        /**
         * See documentation on {@link ClientConfig#getConnectingMinTimeout()}.
         */
        CONNECTING_MIN_TIMEOUT("connecting-min-timeout", Duration.ofSeconds(60L)),

        /**
         * See documentation on {@link ClientConfig#getConnectingMaxTimeout()}.
         */
        CONNECTING_MAX_TIMEOUT("connecting-max-timeout", Duration.ofSeconds(60L)),

        /**
         * See documentation on {@link ClientConfig#getTestingTimeout()}.
         */
        TESTING_TIMEOUT("testing-timeout", Duration.ofSeconds(10L));

        private final String path;
        private final Object defaultValue;

        ClientConfigValue(final String thePath, final Object theDefaultValue) {
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
