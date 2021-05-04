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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import akka.http.javadsl.ClientTransport;

/**
 * Provides configuration settings for the HTTP proxy.
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
     * Converts the proxy settings to an Akka HTTP client transport object.
     * Does not check whether the proxy is enabled.
     *
     * @return an Akka HTTP client transport object matching this config.
     */
    ClientTransport toClientTransport();

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
