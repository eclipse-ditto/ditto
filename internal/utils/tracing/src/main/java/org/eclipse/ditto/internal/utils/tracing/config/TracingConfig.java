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
package org.eclipse.ditto.internal.utils.tracing.config;

import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.internal.utils.tracing.filter.TracingFilter;

/**
 * Provides the configuration settings of tracing.
 */
@Immutable
public interface TracingConfig {

    /**
     * Indicates whether tracing is enabled.
     *
     * @return {@code true} if tracing is enabled, {@code false} if not.
     */
    boolean isTracingEnabled();

    /**
     * Returns the configured context propagation channel.
     *
     * @return the configured context propagation channel.
     */
    String getPropagationChannel();

    /**
     * Returns a {@link TracingFilter} which is derived from configuration.
     *
     * @return the TracingFilter.
     */
    TracingFilter getTracingFilter();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code TracingConfig}.
     */
    enum TracingConfigValue implements KnownConfigValue {

        /**
         * Determines whether tracing is enabled.
         */
        TRACING_ENABLED("enabled", false),

        /**
         * Determines which propagation channel to use. The configured channel has to be configured at
         * {@code kamon.propagation.http.<channel>}.
         */
        TRACING_PROPAGATION_CHANNEL("propagation-channel", "default"),

        /**
         * Defines include and exclude patterns to determine whether to trace matching operation names.
         * By default, all operation names are accepted for tracing – if tracing is enabled at all.
         */
        FILTER("filter", Map.of());

        private final String path;
        private final Object defaultValue;

        private TracingConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

    }

}
