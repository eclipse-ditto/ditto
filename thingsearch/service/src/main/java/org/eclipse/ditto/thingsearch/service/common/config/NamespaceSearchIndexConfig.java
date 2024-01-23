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
package org.eclipse.ditto.thingsearch.service.common.config;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import javax.annotation.concurrent.Immutable;
import java.util.List;

/**
 * Provides configuration settings of the namespace-scoped search indexes.
 * @since 3.5.0
 */
@Immutable
public interface NamespaceSearchIndexConfig {

    /**
     * Returns the namespace pattern definition.
     *
     * @return the namespace pattern definition
     */
    String getNamespacePattern();

    /**
     * Returns a list of fields that will be explicitly included in the search index.
     *
     * @return the search projection fields.
     */
    List<String> getSearchIncludeFields();

    enum NamespaceSearchIndexConfigValue implements KnownConfigValue {

        /**
         * The namespace value to apply the search indexed fields.
         */
        NAMESPACE_PATTERN("namespace-pattern", ""),

        /**
         * The list of fields that will be included in the search DB.
         */
        SEARCH_INCLUDE_FIELDS("search-include-fields", List.of());

        private final String configPath;
        private final Object defaultValue;

        NamespaceSearchIndexConfigValue(final String configPath, final Object defaultValue) {
            this.configPath = configPath;
            this.defaultValue = defaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return configPath;
        }
    }
}
