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
package org.eclipse.ditto.services.gateway.endpoints.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for the {@code messages} resources of the Things service.
 * <p>
 * Java serialization is supported for {@code MessageConfig}.
 * </p>
 */
@Immutable
public interface MessageConfig {

    /**
     * Returns the default timeout of claim messages initiated via /messages resource.
     *
     * @return the default timeout.
     */
    Duration getDefaultTimeout();

    /**
     * Returns the maximum possible timeout of claim messages initiated via /messages resource.
     *
     * @return the maximum timeout.
     */
    Duration getMaxTimeout();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code MessageConfig}.
     */
    enum MessageConfigValue implements KnownConfigValue {

        /**
         * The default timeout of claim messages initiated via /messages resource.
         */
        DEFAULT_TIMEOUT("default-timeout", "35s"),

        /**
         * The maximum possible timeout of claim messages initiated via /messages resource.
         */
        MAX_TIMEOUT("max-timeout", "5.5m");

        private final String path;
        private final Object defaultValue;

        private MessageConfigValue(final String thePath, final Object theDefaultValue) {
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
