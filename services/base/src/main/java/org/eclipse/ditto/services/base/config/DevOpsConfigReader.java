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

import java.time.Duration;

import com.typesafe.config.Config;

/**
 * Reads DevOps configuration.
 */
public final class DevOpsConfigReader extends AbstractConfigReader {

    private static final Duration DEFAULT_NAMESPACE_BLOCK_TIME = Duration.ofMinutes(5L);

    DevOpsConfigReader(final Config config) {
        super(config);
    }

    /**
     * @return effective duration of a block-namespace command.
     */
    public Duration namespaceBlockTime() {
        return getIfPresent("namespace.block-time", config::getDuration).orElse(DEFAULT_NAMESPACE_BLOCK_TIME);
    }

    /**
     * Expose the DevOps config.
     *
     * @return the DevOps config.
     */
    public Config getConfig() {
        return config;
    }
}
