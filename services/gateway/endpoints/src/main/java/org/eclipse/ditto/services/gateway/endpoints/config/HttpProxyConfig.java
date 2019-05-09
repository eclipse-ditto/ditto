/**
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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for the HTTP proxy.
 * <p>
 * Java serialization is supported for {@code HttpProxyConfig}.
 * </p>
 */
@Immutable
public interface HttpProxyConfig {

    /**
     * Indicates whether the HTTP proxy should be enabled.
     *
     * @return {@code true} if the HTTP proxy should be enabled, {@code false} else.
     */
    boolean isEnabled();

    /**
     * Returns the host name of the HTTP proxy.
     *
     * @return the host name
     */
    String getHostname();

    /**
     * Returns the port of the HTTP proxy.
     *
     * @return the port.
     */
    int getPort();

    /**
     * Returns the user name of the HTTP proxy.
     *
     * @return the user name.
     */
    String getUsername();

    /**
     * Returns the password of the HTTP proxy.
     *
     * @return the password.
     */
    String getPassword();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code HttpProxyConfig}.
     */
    enum HttpProxyConfigValue implements KnownConfigValue {

        /**
         * Determines whether the HTTP proxy should be enabled.
         */
        ENABLED("enabled", false),

        /**
         * The host name of the HTTP proxy.
         */
        HOST_NAME("hostname", ""),

        /**
         * The port of the HTTP proxy.
         */
        PORT("port", 0),

        /**
         * The user name of the HTTP proxy.
         */
        USER_NAME("username", ""),

        /**
         * The password of the HTTP proxy.
         */
        PASSWORD("password", "");

        private final String path;
        private final Object defaultValue;

        private HttpProxyConfigValue(final String thePath, final Object theDefaultValue) {
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
