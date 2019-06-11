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
 * Provides configuration settings of the DevOps endpoint.
 */
@Immutable
public interface DevOpsConfig {

    /**
     * Indicates whether DevOps status resources (e. g. /status) should be secured with BasicAuth or not.
     *
     * @return {@code true} if resources should be secured with BasicAuth, {@code false} else;
     */
    boolean isSecureStatus();

    /**
     * Returns the BasicAuth password of all DevOps resources.
     *
     * @return the password.
     */
    String getPassword();

    /**
     * Returns the BasicAuth password for status resources only.
     *
     * @return the status password.
     */
    String getStatusPassword();

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
         * The BasicAuth password of all DevOps resources.
         */
        PASSWORD("password", "foobar"),

        /**
         * The BasicAuth password for status resources only.
         */
        STATUS_PASSWORD("statusPassword", "status");

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
