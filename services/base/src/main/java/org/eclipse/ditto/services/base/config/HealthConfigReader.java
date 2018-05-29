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
 * Health-check configurations.
 */
public final class HealthConfigReader extends AbstractConfigReader {

    /**
     * Default: Whether the health check should be enabled (globally) or not.
     */
    private static final boolean DEFAULT_ENABLED = true;

    /**
     * Default: The interval of the health check.
     */
    private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(60L);


    /**
     * Default: Whether the persistence health check should be enabled or not.
     */
    private static final boolean DEFAULT_PERSISTENCE_ENABLED = false;

    /**
     * Default: The timeout of the health check for persistence. If the persistence takes longer than that to respond,
     * it is considered "DOWN".
     */
    private static final Duration DEFAULT_PERSISTENCE_TIMEOUT = Duration.ofSeconds(60L);

    private static final String KEY_ENABLED = "enabled";
    private static final String PATH_ENABLED = KEY_ENABLED;
    private static final String PATH_INTERVAL = "interval";

    private static final String PATH_PERSISTENCE = "persistence";
    private static final String PATH_PERSISTENCE_ENABLED = path(PATH_PERSISTENCE, KEY_ENABLED);
    private static final String PATH_PERSISTENCE_TIMEOUT = path(PATH_PERSISTENCE, "timeout");

    HealthConfigReader(final Config config) {
        super(config);
    }

    /**
     * Get whether the health check should be enabled (globally) or not.
     *
     * @return whether the health check should be enabled (globally) or not.
     */
    public boolean enabled() {
        return getIfPresent(PATH_ENABLED, config::getBoolean).orElse(DEFAULT_ENABLED);
    }

    /**
     * Get the interval of the health check.
     *
     * @return the interval of the health check.
     */
    public Duration getInterval() {
        return getIfPresent(PATH_INTERVAL, config::getDuration).orElse(DEFAULT_INTERVAL);
    }

    /**
     * Get whether the persistence health check should be enabled or not.
     *
     * @return whether the  persistence health check should be enabled or not.
     */
    public boolean persistenceEnabled() {
        return getIfPresent(PATH_PERSISTENCE_ENABLED, config::getBoolean).orElse(DEFAULT_PERSISTENCE_ENABLED);
    }

    /**
     * Get the timeout of the health check for persistence. If the persistence takes longer than that to respond,
     * it is considered "DOWN".
     *
     * @return the timeout of the health check for persistence.
     */
    public Duration getPersistenceTimeout() {
        return getIfPresent(PATH_PERSISTENCE_TIMEOUT, config::getDuration).orElse(DEFAULT_PERSISTENCE_TIMEOUT);
    }

}
