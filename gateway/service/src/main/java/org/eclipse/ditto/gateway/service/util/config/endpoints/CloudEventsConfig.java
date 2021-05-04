/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.util.config.endpoints;

import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.DittoConstants;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import akka.http.javadsl.model.MediaTypes;

/**
 * Provides configuration settings for the cloud events endpoint of the Ditto Gateway service.
 */
@Immutable
public interface CloudEventsConfig {

    /**
     * Returns if an empty data schema is allowed.
     *
     * @return {@code true} if an empty data schema is allowed {@code false} otherwise.
     */
    boolean isEmptySchemaAllowed();

    /**
     * Returns the allowed data types.
     *
     * @return The set of allowed data types.
     */
    Set<String> getDataTypes();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code CloudEventsConfig}.
     */
    enum CloudEventsConfigValue implements KnownConfigValue {

        /**
         * Flag if an empty data schema is allowed.
         */
        EMPTY_SCHEMA_ALLOWED("empty-schema-allowed", true),

        /**
         * Set of allowed data types
         */
        DATA_TYPES("data-types",
                Set.of(MediaTypes.APPLICATION_JSON.toString(), DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE));

        private final String path;
        private final Object defaultValue;

        private CloudEventsConfigValue(final String thePath, final Object theDefaultValue) {
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
