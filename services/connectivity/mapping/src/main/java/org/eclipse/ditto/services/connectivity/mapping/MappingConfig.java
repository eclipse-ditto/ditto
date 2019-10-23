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
package org.eclipse.ditto.services.connectivity.mapping;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.connectivity.mapping.javascript.JavaScriptConfig;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for Connectivity service's message mapping behaviour.
 */
@Immutable
public interface MappingConfig {

    /**
     * Returns the config of the JavaScript message mapping.
     *
     * @return the config.
     */
    JavaScriptConfig getJavaScriptConfig();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code MappingConfig}.
     */
    enum MappingConfigValue implements KnownConfigValue {

        /**
         * The name of the class which is used for creating message mapping objects.
         */
        FACTORY("factory", "org.eclipse.ditto.services.connectivity.mapping.MessageMappers");

        private final String path;
        private final Object defaultValue;

        MappingConfigValue(final String thePath, final Object theDefaultValue) {
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
