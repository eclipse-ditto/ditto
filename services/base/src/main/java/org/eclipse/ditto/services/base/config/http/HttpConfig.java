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
package org.eclipse.ditto.services.base.config.http;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides the configuration settings of the Ditto HTTP endpoint.
 * <p>
 * Java serialization is supported for {@code HttpConfig}.
 * </p>
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
        PORT("port", 8080);

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
