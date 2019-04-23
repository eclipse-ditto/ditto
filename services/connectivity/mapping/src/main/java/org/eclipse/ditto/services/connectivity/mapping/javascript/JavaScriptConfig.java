/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.mapping.javascript;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for Connectivity service's JavaScript message mapping behaviour.
 * <p>
 * Java serialization is supported for {@code JavaScriptConfig}.
 * </p>
 */
@Immutable
public interface JavaScriptConfig {

    /**
     * Returns the maximum script size in bytes of a mapping script to run.
     * This value is meant to prevent loading big JS dependencies into the script
     * (e. g. jQuery which has ~250 kB).
     *
     * @return the maximum script size.
     */
    int getMaxScriptSizeBytes();

    /**
     * Returns the maximum execution time of a mapping script to run.
     * This prevents endless loops and too complex scripts.
     *
     * @return the maximum script execution time.
     */
    Duration getMaxScriptExecutionTime();

    /**
     * Returns the maximum call stack depth in the mapping script.
     * This prevents recursions or other too complex computation.
     *
     * @return the maximum script stack depth.
     */
    int getMaxScriptStackDepth();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code JavaScriptConfig}.
     */
    enum JavaScriptConfigValue implements KnownConfigValue {

        /**
         * The maximum script size in bytes of a mapping script to run.
         */
        MAX_SCRIPT_SIZE_BYTES("maxScriptSizeBytes", 50_000),

        /**
         * The maximum execution time of a mapping script to run.
         */
        MAX_SCRIPT_EXECUTION_TIME("maxScriptExecutionTime", Duration.ofMillis(500L)),

        /**
         * The maximum call stack depth in the mapping script.
         */
        MAX_SCRIPT_STACK_DEPTH("maxScriptStackDepth", 10);

        private final String path;
        private final Object defaultValue;

        private JavaScriptConfigValue(final String thePath, final Object theDefaultValue) {
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
