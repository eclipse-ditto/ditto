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
package org.eclipse.ditto.base.service.config.supervision;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for the Actor supervision.
 */
@Immutable
public interface SupervisorConfig {

    /**
     * Returns the config for the exponential back-off strategy.
     *
     * @return the config.
     */
    ExponentialBackOffConfig getExponentialBackOffConfig();

    /** Returns the config for supervisor local ACK timeout calls.
     * @return the config.
     */
    LocalAskTimeoutConfig getLocalAskTimeoutConfig();

    /**
     * Returns how long a supervisor waits for in-flight operations after it was asked to stop (passivation or shard
     * hand-off) before it stops anyway.
     *
     * @return the shutdown timeout.
     */
    Duration getShutdownTimeout();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code SupervisorConfig}.
     */
    enum SupervisorConfigValue implements KnownConfigValue {

        /**
         * How long a deferred stop waits for in-flight operations. Should be above the duration of the slowest
         * legitimate operation and below the coordinated-shutdown phase {@code cluster-sharding-shutdown-region}.
         */
        SHUTDOWN_TIMEOUT("shutdown-timeout", Duration.ofSeconds(12L));

        private final String path;
        private final Duration defaultValue;

        SupervisorConfigValue(final String thePath, final Duration theDefaultValue) {
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
