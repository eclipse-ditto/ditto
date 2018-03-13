/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.base.config;

import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import com.typesafe.config.Config;

/**
 * Bare-bone implementation of service config reader. Reads only cluster and statsd configurations.
 */
@Immutable
public final class DittoServiceConfigReader extends AbstractServiceConfigReader {

    private DittoServiceConfigReader(final Config config, final String serviceName) {
        super(config, serviceName);
    }

    /**
     * Create a default service config reader.
     *
     * @param serviceName name of the service.
     * @return the service config reader.
     */
    public static Function<Config, ServiceConfigReader> from(final String serviceName) {
        return config -> new DittoServiceConfigReader(config, serviceName);
    }
}
