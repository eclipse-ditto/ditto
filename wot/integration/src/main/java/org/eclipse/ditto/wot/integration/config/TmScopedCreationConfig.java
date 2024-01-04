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
package org.eclipse.ditto.wot.integration.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for WoT (Web of Things) integration regarding the creation of Thing and Feature
 * skeletons based on WoT ThingModels.
 *
 * @since 3.5.0
 */
@Immutable
public interface TmScopedCreationConfig {

    /**
     * Returns whether the creation of a Thing/Feature skeleton based on an on creation contained WoT ThingModel in the
     * Thing's {@code ThingDefinition} for {@code CreateThing} commands should be enabled or not.
     *
     * @return whether the TM based Thing skeleton creation should be enabled or not.
     */
    boolean isSkeletonCreationEnabled();

    /**
     * Returns whether for optional marked properties in the WoT ThingModel properties should be generated based on their
     * defaults.
     *
     * @return whether for optional marked properties in the WoT ThingModel properties should be generated.
     */
    boolean shouldGenerateDefaultsForOptionalProperties();

    /**
     * Returns whether for WoT related errors (e.g. not downloadable WoT ThingModel or not parsable ThingModel) exceptions
     * should be thrown, or they should be swallowed silently and as result no skeleton should be created.
     *
     * @return whether for WoT related errors exceptions should be thrown
     */
    boolean shouldThrowExceptionOnWotErrors();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code TmScopedCreationConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Whether the TM based Thing skeleton creation should be enabled or not.
         */
        SKELETON_CREATION_ENABLED("skeleton-creation-enabled", true),

        /**
         * Whether for the Thing skeleton creation, defaults for optional properties should be generated.
         */
        GENERATE_DEFAULTS_FOR_OPTIONAL_PROPERTIES("generate-defaults-for-optional-properties", false),

        /**
         * Whether during Thing skeleton creation, exceptions should be thrown if e.g. a WoT model could not be resolved
         * or was invalid.
         */
        THROW_EXCEPTION_ON_WOT_ERRORS("throw-exception-on-wot-errors", true);


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
