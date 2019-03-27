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
