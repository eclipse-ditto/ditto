/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.common.config;

import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides the configuration settings for a custom search index.
 *
 * @since 3.9.0
 */
@Immutable
public interface CustomSearchIndexConfig {

    /**
     * Returns the name of the custom index (from the config map key).
     *
     * @return the index name.
     */
    String getName();

    /**
     * Returns the list of fields that make up this compound index.
     *
     * @return the list of field configurations.
     */
    List<CustomSearchIndexFieldConfig> getFields();

    /**
     * An enumeration of the known config path expressions and their associated default values.
     */
    enum CustomSearchIndexConfigValue implements KnownConfigValue {

        /**
         * The list of fields in the compound index.
         */
        FIELDS("fields", List.of());

        private final String path;
        private final Object defaultValue;

        CustomSearchIndexConfigValue(final String thePath, final Object theDefaultValue) {
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
