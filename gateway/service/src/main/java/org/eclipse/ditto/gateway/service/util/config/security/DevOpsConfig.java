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
package org.eclipse.ditto.gateway.service.util.config.security;

import java.util.Collection;
import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of the DevOps endpoint.
 */
@Immutable
public interface DevOpsConfig {

    /**
     * Indicates whether DevOps resources (e. g. /status and /devops) should be secured or not.
     *
     * @return {@code true} if resources should be secured, {@code false} else;
     */
    boolean isSecured();

    /**
     * Returns the authentication method for DevOps resources.
     *
     * @return the authentication method.
     */
    DevopsAuthenticationMethod getDevopsAuthenticationMethod();

    /**
     * Returns the BasicAuth password of all DevOps resources.
     *
     * @return the password.
     */
    String getPassword();

    /**
     * Returns the OAuth2 JWT token subject required for accessing DevOps resources.
     *
     * @return the public key provider.
     */
    Collection<String> getDevopsOAuth2Subjects();

    /**
     * Returns the authentication method for status resources.
     *
     * @return the authentication method.
     */
    DevopsAuthenticationMethod getStatusAuthenticationMethod();

    /**
     * Returns the BasicAuth password for status resources only.
     *
     * @return the status password.
     */
    String getStatusPassword();

    /**
     * Returns the oauth config for devops and status resources.
     *
     * @return the oauth config.
     */
    OAuthConfig getOAuthConfig();

    /**
     * Returns the OAuth2 JWT token subject required for accessing status resources.
     *
     * @return the public key provider.
     */
    Collection<String> getStatusOAuth2Subjects();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code DevOpsConfig}.
     */
    enum DevOpsConfigValue implements KnownConfigValue {

        /**
         * Determines whether DevOps resources (e. g. /status and /devops) should be secured or not.
         */
        SECURED("secured", true),

        /**
         * The authentication method for DevOps resources.
         */
        DEVOPS_AUTHENTICATION_METHOD("devops-authentication-method", "basic"),

        /**
         * The BasicAuth password of all DevOps resources.
         */
        PASSWORD("password", "foobar"),

        /**
         * The OAuth2 JWT token subject required for accessing DevOps resources.
         */
        DEVOPS_OAUTH2_SUBJECTS("devops-oauth2-subjects", List.of()),

        /**
         * The authentication method for status resources.
         */
        STATUS_AUTHENTICATION_METHOD("status-authentication-method", "basic"),

        /**
         * The BasicAuth password for status resources only.
         */
        STATUS_PASSWORD("statusPassword", "status"),

        /**
         * Returns the OAuth2 JWT token subject required for accessing status resources.
         */
        STATUS_OAUTH2_SUBJECTS("status-oauth2-subjects", List.of());

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
