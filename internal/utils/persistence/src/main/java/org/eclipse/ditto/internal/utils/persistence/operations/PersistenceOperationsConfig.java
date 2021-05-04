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
package org.eclipse.ditto.internal.utils.persistence.operations;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for persistence operations.
 */
@Immutable
public interface PersistenceOperationsConfig {

    /**
     * Returns the duration to wait before starting the actual purging after a persistence actor has been shut down.
     * This is required to allow the persistence actor to write the last snapshot/event when shutting down before the
     * purging takes place.
     *
     * @return the delay.
     */
    Duration getDelayAfterPersistenceActorShutdown();

    /**
     * An enumeration of known value paths and associated default values of the PersistenceOperationsConfig.
     */
    enum PersistenceOperationsConfigValue implements KnownConfigValue {

        /**
         * The duration to wait before starting the actual purging after a persistence actor has been shut down.
         */
        DELAY_AFTER_PERSISTENCE_ACTOR_SHUTDOWN("delay-after-persistence-actor-shutdown", Duration.ofSeconds(5L));

        private final String path;
        private final Object defaultValue;

        private PersistenceOperationsConfigValue(final String path, final Object defaultValue) {
            this.path = path;
            this.defaultValue = defaultValue;
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
