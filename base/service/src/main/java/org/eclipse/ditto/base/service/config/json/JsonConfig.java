/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.service.config.json;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides the configuration settings of Ditto JSON.
 */
@Immutable
public interface JsonConfig {

    /**
     * Returns the factor of the buffer size used when escaping JSON strings.
     *
     * @return the factor of the buffer size used when escaping JSON strings.
     */
    double getEscapingBufferFactor();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code JsonConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * The factor of the buffer size used when escaping JSON strings.
         */
        ESCAPING_BUFFER_FACTOR("escaping-buffer-factor", 1.0);

        private final String path;
        private final Object defaultValue;

        private ConfigValue(final String thePath, final Object theDefaultValue) {
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
