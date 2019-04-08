/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.base.config;

import org.eclipse.ditto.services.utils.config.AbstractConfigReader;

import com.typesafe.config.Config;

/**
 * Http-service configurations.
 */
public final class HttpConfigReader extends AbstractConfigReader {

    private static final String PATH_HOSTNAME =  "hostname";
    private static final String PATH_PORT = "port";

    HttpConfigReader(final Config config) {
        super(config);
    }

    /**
     * Get the hostname value of the HTTP service or an empty string.
     *
     * @return the hostname value of the HTTP service or an empty string, if not set.
     */
    public String getHostname() {
        return getIfPresent(PATH_HOSTNAME, config::getString).orElse("");
    }

    /**
     * Get the port number of the HTTP service.
     *
     * @return the port number of the HTTP service.
     * @throws com.typesafe.config.ConfigException.Missing if the port number is missing.
     */
    public int getPort() {
        return config.getInt(PATH_PORT);
    }


}
