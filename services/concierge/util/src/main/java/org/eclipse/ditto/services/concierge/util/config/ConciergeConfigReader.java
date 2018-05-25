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
