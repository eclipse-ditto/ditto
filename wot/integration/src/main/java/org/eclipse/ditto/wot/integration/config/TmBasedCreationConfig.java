/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
 * @since 2.4.0
 */
@Immutable
public interface TmBasedCreationConfig {

    /**
     * Returns whether the creation of a Thing skeleton based on an on creation contained WoT ThingModel in the
     * Thing's {@code ThingDefinition} for {@code CreateThing} commands should be enabled or not.
     *
     * @return whether the TM based Thing skeleton creation should be enabled or not.
     */
    boolean isThingSkeletonCreationEnabled();

    /**
     * Returns whether the creation of a Feature skeleton based on an on creation contained WoT ThingModel in the
     * Features' {@code FeatureDefinition} for {@code ModifyFeature} commands (which create a new feature)
     * should be enabled or not.
     *
     * @return whether the TM based Feature skeleton creation should be enabled or not.
     */
    boolean isFeatureSkeletonCreationEnabled();


    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code TmBasedCreationConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Whether the TM based Thing skeleton creation should be enabled or not.
         */
        THING_SKELETON_CREATION_ENABLED("thing-skeleton-creation-enabled", true),

        /**
         * Whether the TM based Feature skeleton creation should be enabled or not.
         */
        FEATURE_SKELETON_CREATION_ENABLED("feature-skeleton-creation-enabled", true);


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
