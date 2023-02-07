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
package org.eclipse.ditto.things.service.common.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.supervision.WithSupervisorConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.EventConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.WithActivityCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.WithSnapshotConfig;
import org.eclipse.ditto.internal.utils.persistentactors.cleanup.WithCleanupConfig;

/**
 * Provides configuration settings for thing entities.
 */
@Immutable
public interface ThingConfig extends WithSupervisorConfig, WithActivityCheckConfig, WithSnapshotConfig,
        WithCleanupConfig {

    /**
     * Returns the config of the thing event journal behaviour.
     *
     * @return the config.
     */
    EventConfig getEventConfig();

    /**
     * Get the timeout waiting for responses and acknowledgements during coordinated shutdown.
     *
     * @return The timeout.
     */
    Duration getShutdownTimeout();

    /**
     * An enumeration of the known config path expressions and their associated default values for {@code ThingConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Timeout waiting for responses and acknowledgements during coordinated shutdown.
         */
        SHUTDOWN_TIMEOUT("shutdown-timeout", Duration.ofSeconds(3));

        private final String path;
        private final Object defaultValue;

        ConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }
}
