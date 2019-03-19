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
package org.eclipse.ditto.services.utils.persistence.mongo.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for checking the index initialization.
 * <p>
 * Java serialization is supported for {@code IndexInitializationConfig}.
 * </p>
 */
@Immutable
public interface IndexInitializationConfig {

    /**
     * Indicates if index initializationConfig is enabled.
     *
     * @return {@code true} if index initialization is enabled, {@code false} else.
     */
    boolean isIndexInitializationConfigEnabled();


    enum IndexInitializerConfigValue implements KnownConfigValue {

        /**
         * Determines whether minimal information for all incoming messages should be logged.
         * This enables message tracing throughout the system.
         */
        ENABLED("enabled", true);

        private final String path;
        private final Object defaultValue;

        private IndexInitializerConfigValue(final String thePath, final Object theDefaultValue) {
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
