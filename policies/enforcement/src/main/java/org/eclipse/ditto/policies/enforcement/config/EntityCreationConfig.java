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
package org.eclipse.ditto.policies.enforcement.config;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for Concierge entity creation behaviour.
 */
@Immutable
public interface EntityCreationConfig {

    /**
     * Returns the list of creation config entries which would allow the creation.
     *
     * An empty list would <strong>not</strong> allow any entity to be created. You must have at least one
     * entry, even if it is without restrictions.
     *
     * @return the list of entries.
     */
    List<CreationRestrictionConfig> getGrant();

    /**
     * Returns the list of creation config entries which would reject the creation.
     *
     * @return the list of entries.
     */
    List<CreationRestrictionConfig> getRevoke();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code EntityCreationConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * The list of creation config entries which would allow the creation.
         */
        GRANT("grant", List.of(Map.of())),

        /**
         * The list of creation config entries which would reject the creation.
         */
        REVOKE("revoke", Collections.emptyList());

        private final String path;
        private final Object defaultValue;

        ConfigValue(final String thePath, final Object theDefaultValue) {
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
