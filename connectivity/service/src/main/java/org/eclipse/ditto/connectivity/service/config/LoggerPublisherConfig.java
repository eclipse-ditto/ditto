/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.config;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.model.LogLevel;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Config for the connection log publisher to a fluentd/fluentbit endpoint.
 */
@Immutable
public interface LoggerPublisherConfig {

    /**
     * Indicates whether publishing connection logs to a fluentd/fluentbit endpoint should be enabled.
     *
     * @return {@code true} if connection logs should be published.
     */
    boolean isEnabled();

    /**
     * Returns the log levels to include for the publisher logs.
     *
     * @return the log levels to include for the publisher logs.
     */
    Set<LogLevel> getLogLevels();

    /**
     * Indicates whether the published log entries should contain headers and payloads if those were available.
     *
     * @return {@code true} if published connection logs should contain headers and payloads.
     */
    boolean isLogHeadersAndPayload();

    /**
     * Returns a specific log-tag to use for the published logs. If empty, a default log-tag will be used:
     * {@code connection:<connection-id>}.
     *
     * @return the optional specific log-tag to use for published logs.
     */
    Optional<String> getLogTag();

    /**
     * Returns additional log context to add to the published log entries.
     *
     * @return the additional log context.
     */
    Map<String, Object> getAdditionalLogContext();

    /**
     * Returns the config for the fluency library used to forward logs to fluentd/fluentbit.
     *
     * @return the fluency library config.
     */
    FluencyLoggerPublisherConfig getFluencyLoggerPublisherConfig();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code LoggerPublisherConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Whether publishing to fluentd/fluentbit publishing is enabled.
         */
        ENABLED("enabled", false),

        /**
         * The log levels to include for the publisher logs.
         */
        LOG_LEVELS("logLevels", LogLevel.SUCCESS.getLevel() + "," + LogLevel.FAILURE.getLevel()),

        /**
         * Whether to log headers and payload for publisher logs.
         */
        LOG_HEADERS_AND_PAYLOAD("logHeadersAndPayload", false),

        /**
         * The optional specific log-tag to use for published logs.
         */
        LOG_TAG("log-tag", null),

        /**
         * The additional log context to add to log entries.
         */
        ADDITIONAL_LOG_CONTEXT("additional-log-context", Collections.emptyMap());

        private final String path;
        private final Object defaultValue;

        ConfigValue(final String thePath, @Nullable final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        @Nullable
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }
}

