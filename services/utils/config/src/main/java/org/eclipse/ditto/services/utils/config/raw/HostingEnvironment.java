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
package org.eclipse.ditto.services.utils.config.raw;

/**
 * An enumeration of the known hosting environments.
 */
enum HostingEnvironment {

    CLOUD_NATIVE("-cloud"),

    DOCKER("-docker"),

    FILE_BASED_CONFIGURED(""),

    FILE_BASED_SERVICE_NAME(""),

    DEVELOPMENT("-dev");

    public static final String CONFIG_PATH = "hosting.environment";

    private final String configFileSuffix;

    private HostingEnvironment(final String suffix) {
        configFileSuffix = suffix;
    }

    /**
     * Returns the suffix that distinguishes the config file for this particular hosting environment from the base
     * config file.
     *
     * @return the suffix.
     */
    public String getConfigFileSuffix() {
        return configFileSuffix;
    }

}
