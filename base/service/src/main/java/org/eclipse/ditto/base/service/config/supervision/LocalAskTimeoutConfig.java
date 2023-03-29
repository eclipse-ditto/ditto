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
     * @return the duration for a local ACK timeout calls.
     */
    Duration getLocalAckTimeout();


    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code LocalAskTimeoutConfigValue}.
     */
    enum LocalAskTimeoutConfigValue implements KnownConfigValue {

        /**
         * The local ACK timeout duration.
         */
        ASK_TIMEOUT("timeout", Duration.ofSeconds(5L));

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
