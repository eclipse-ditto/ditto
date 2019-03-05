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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for the Gateway authentication.
 * <p>
 * Java serialization is supported for {@code AuthenticationConfig}.
 * </p>
 */
@Immutable
public interface AuthenticationConfig {

    /**
     * Returns the configuration settings of the HTTP proxy.
     *
     * @return the config.
     */
    HttpProxyConfig getHttpProxyConfig();

    /**
     * Indicates whether dummy authentication should be enabled.
     *
     * @return {@code true} if dummy authentication is enabled, {@code false} else.
     */
    boolean isDummyAuthenticationEnabled();

    /**
     * Returns the configuration settings of the DevOps resources.
     *
     * @return the config.
     */
    DevOpsConfig getDevOpsConfig();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code AuthenticationConfig}.
     */
    enum AuthenticationConfigValue implements KnownConfigValue {

        /**
         * Determines whether dummy authentication should be enabled.
         */
        DUMMY_AUTH_ENABLED("dummy.enabled", false);

        private final String path;
        private final Object defaultValue;

        private AuthenticationConfigValue(final String thePath, final Object theDefaultValue) {
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

    /**
     * Provides configuration settings for the HTTP proxy.
     * <p>
     * Java serialization is supported for {@code HttpProxyConfig}.
     * </p>
     */
    @Immutable
    interface HttpProxyConfig {

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

    /**
     * Provides configuration settings of the DevOps endpoint.
     * <p>
     * Java serialization is supported for {@code DevOpsConfig}.
     * </p>
     */
    @Immutable
    interface DevOpsConfig {

        /**
         * Indicates whether DevOps status resources (e. g. /status) should be secured with BasicAuth or not.
         *
         * @return {@code true} if resources should be secured with BasicAuth, {@code false} else;
         */
        boolean isSecureStatus();

        /**
         * Returns the BasicAuth password of the DevOps resources.
         *
         * @return the password.
         */
        String getPassword();

        /**
         * An enumeration of the known config path expressions and their associated default values for
         * {@code DevOpsConfig}.
         */
        enum DevOpsConfigValue implements KnownConfigValue {

            /**
             * Determines whether DevOps status resources (e. g. /status) should be secured with BasicAuth or not.
             */
            SECURE_STATUS("securestatus", true),

            /**
             * The BasicAuth password of the DevOps resources.
             */
            PASSWORD("password", "foobar");

            private final String path;
            private final Object defaultValue;

            private DevOpsConfigValue(final String thePath, final Object theDefaultValue) {
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

}
