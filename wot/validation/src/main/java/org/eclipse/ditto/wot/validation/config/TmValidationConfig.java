/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.validation.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for WoT (Web of Things) integration regarding the validation of Things and Features
 * based on their WoT ThingModels.
 *
 * @since 3.6.0
 */
@Immutable
public interface TmValidationConfig {

    /**
     * @return whether the ThingModel validation of Things/Features should be enabled or not.
     */
    boolean isEnabled();

    /**
     * @return the config for validating things.
     */
    ThingValidationConfig getThingValidationConfig();

    /**
     * @return the config for validating features.
     */
    FeatureValidationConfig getFeatureValidationConfig();


    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code TmValidationConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Whether the TM based validation should be enabled or not.
         */
        ENABLED("enabled", true);


        private final String path;
        private final Object defaultValue;

        ConfigValue(final String thePath, final Object theDefaultValue) {
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
