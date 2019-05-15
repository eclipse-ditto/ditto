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

}
