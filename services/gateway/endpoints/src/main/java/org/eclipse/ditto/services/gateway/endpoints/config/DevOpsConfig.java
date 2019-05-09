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
 * Provides configuration settings of the DevOps endpoint.
 * <p>
 * Java serialization is supported for {@code DevOpsConfig}.
 * </p>
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
