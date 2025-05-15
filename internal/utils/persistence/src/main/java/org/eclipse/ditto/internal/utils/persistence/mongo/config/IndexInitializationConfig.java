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
package org.eclipse.ditto.internal.utils.persistence.mongo.config;

import java.util.List;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for checking the index initialization.
 */
@Immutable
public interface IndexInitializationConfig {

    /**
     * Indicates if index initializationConfig is enabled.
     *
     * @return {@code true} if index initialization is enabled, {@code false} else.
     */
    boolean isIndexInitializationConfigEnabled();

    /**
     * The configuration which indexes should be active (automatically created and not configured ones deleted).
     *
     * @return the set of activated index names.
     */
    Set<String> getActivatedIndexNames();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * IndexInitializationConfig.
     */
    enum IndexInitializerConfigValue implements KnownConfigValue {

        /**
         * Determines whether minimal information for all incoming messages should be logged.
         * This enables message tracing throughout the system.
         */
        ENABLED("enabled", true),

        /**
         * Holds the configuration which indexes should be active (automatically created and not configured ones deleted).
         */
        ACTIVATED_INDEX_NAMES("activated-index-names",
                List.of(
                        "_namespace",
                        "global_read",
                        "v_wildcard",
                        "policyId",
                        "referencedPolicies",
                        "deleteAt"
                )
        );

        private final String path;
        private final Object defaultValue;

        IndexInitializerConfigValue(final String thePath, final Object theDefaultValue) {
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
