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
package org.eclipse.ditto.services.thingsearch.common.config;

import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.ServiceSpecificConfig;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;
import org.eclipse.ditto.services.utils.health.config.WithHealthCheckConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.WithIndexInitializationConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.WithMongoDbConfig;

/**
 * Provides the configuration settings of the Search service.
 * <p>
 * Java serialization is supported for SearchConfig.
 * </p>
 */
@Immutable
public interface SearchConfig
        extends ServiceSpecificConfig, WithHealthCheckConfig, WithMongoDbConfig, WithIndexInitializationConfig {

    Optional<String> getMongoHintsByNamespace();

    /**
     * Returns the configuration settings of the "delete" section.
     *
     * @return the config.
     */
    DeleteConfig getDeleteConfig();

    /**
     * Returns the configuration settings for the physical deletion of thing entities that are marked as
     * {@code "__deleted"}.
     *
     * @return the config.
     */
    DeletionConfig getDeletionConfig();

    /**
     * Returns the configuration settings for the search updating functionality.
     *
     * @return the config.
     */
    UpdaterConfig getUpdaterConfig();

    /**
     * Returns the configuration settings
     *
     * @return the config.
     */
    StreamConfig getStreamConfig();

    /**
     * An enumeration of the known config path expressions and their associated default values for SearchConfig.
     */
    enum SearchConfigValue implements KnownConfigValue {

        /**
         * Default value is {@code null}.
         */
        MONGO_HINTS_BY_NAMESPACE("mongo-hints-by-namespace", null);

        private final String path;
        private final Object defaultValue;

        private SearchConfigValue(final String path, final Object defaultValue) {
            this.path = path;
            this.defaultValue = defaultValue;
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
