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
package org.eclipse.ditto.internal.utils.config.raw;

import javax.annotation.Nullable;

/**
 * An enumeration of the known hosting environments.
 */
enum HostingEnvironment {

    /**
     * Used when hosting environment is set to "production", or one of the deprecated "docker", "cloud".
     * This will use no additional config file in addition to $servicename.conf
     */
    PRODUCTION,

    /**
     * Used when no (known) hosting environment is specified.
     * This will use the config file $servicename-dev.conf
     */
    DEVELOPMENT,

    /**
     * Used when hosting environment is set to "filebased".
     * This will use the config file specified by the environment variable: HOSTING_ENVIRONMENT_FILE_LOCATION.
     */
    FILE_BASED;

    static HostingEnvironment fromHostingEnvironmentName(@Nullable final String hostingEnvironmentName) {
        if (hostingEnvironmentName == null) {
            return DEVELOPMENT;
        }
        return switch (hostingEnvironmentName.toLowerCase()) {  // deprecated but support for backward compatibility reasons
            case "docker", "cloud", "production" -> HostingEnvironment.PRODUCTION;
            case "filebased" -> HostingEnvironment.FILE_BASED;
            default -> HostingEnvironment.DEVELOPMENT;
        };
    }

    @Override
    public String toString() {
        return name();
    }
}
