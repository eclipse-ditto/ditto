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
package org.eclipse.ditto.services.concierge.util.config;

import java.util.function.Function;

import com.typesafe.config.Config;

/**
 * The default configuration reader for the concierge service.
 */
public final class ConciergeConfigReader extends AbstractConciergeConfigReader {

    private ConciergeConfigReader(final Config config, final String serviceName) {
        super(config, serviceName);
    }

    /**
     * Create configuration reader for concierge service.
     *
     * @param serviceName name of the concierge service.
     * @return function to create the configuration reader.
     */
    public static Function<Config, ConciergeConfigReader> from(final String serviceName) {
        return config -> new ConciergeConfigReader(config, serviceName);
    }

}
