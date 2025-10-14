/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
 * Provides configuration settings for the local ACK timeout.
 */
@Immutable
public interface LocalAskTimeoutConfig {

    /**
     * Timeout for local actor invocations - a small timeout should be more than sufficient as those are just method
     * calls.
     * @return the duration for local ask timeout calls.
     */
    Duration getLocalAskTimeout();

    /**
     * Timeout for local actor invocations during Persistence Actor recovery - then a bigger timeout than the default
     * {@link #getLocalAskTimeout()} is needed as the persistence actor's state has first to be recovered from the DB.
     *
     * @return the duration for local ask timeout calls where the persistence actor is in recovery.
     */
    Duration getLocalAskTimeoutDuringRecovery();


    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code LocalAskTimeoutConfigValue}.
     */
    enum LocalAskTimeoutConfigValue implements KnownConfigValue {

        /**
         * The local ask timeout duration.
         */
        ASK_TIMEOUT("timeout", Duration.ofSeconds(2L)),

        /**
         * The local ask timeout duration during persistence actor recovery.
         */
        ASK_TIMEOUT_DURING_RECOVERY("timeout-during-recovery", Duration.ofSeconds(45L));

        private final String path;
        private final Duration defaultValue;

        LocalAskTimeoutConfigValue(final String thePath, final Duration theDefaultValue) {

            this.path = thePath;
            this.defaultValue = theDefaultValue;
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
