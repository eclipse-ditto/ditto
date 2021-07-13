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

package org.eclipse.ditto.connectivity.service.config.mapping;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for Connectivity service's message mapper behaviour.
 */
@Immutable
public interface MapperLimitsConfig {

    /**
     * Returns the maximum number of mapper which could be defined in one source
     *
     * @return the maximum number of mapper in one source
     */
    int getMaxSourceMappers();

    /**
     * Returns the maximum number of messages which could be invoked by one mapper
     * defined in source
     *
     * @return the maximum number of messages invoked by a source defined mapper
     */
    int getMaxMappedInboundMessages();

    /**
     * Returns the maximum number of mapper which could be defined in one target
     *
     * @return the maximum number of mapper in one target
     */
    int getMaxTargetMappers();

    /**
     * Returns the maximum number of messages which could be invoked by one mapper
     * defined in target
     *
     * @return the maximum number of messages invoked by a target defined mapper
     */
    int getMaxMappedOutboundMessages();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code MapperLimitsConfig}.
     */
    enum MapperLimitsConfigValue implements KnownConfigValue {

        /**
         * Maximum number of mappers defined in one source
         */
        MAX_SOURCE_MAPPERS("max-source-mappers", 10),

        /**
         * Maximum number of messages invoked by a mapper defined in source
         */
        MAX_MAPPED_INBOUND_MESSAGE("max-mapped-inbound-messages", 10),

        /**
         * Maximum number of mappers defined in one target
         */
        MAX_TARGET_MAPPERS("max-target-mappers", 10),

        /**
         * Maximum number of messages invoked by a mapper defined in target
         */
        MAX_MAPPED_OUTBOUND_MESSAGE("max-mapped-outbound-messages", 10);


        private final String path;
        private final Object defaultValue;

        MapperLimitsConfigValue(final String thePath, final Object theDefaultValue) {
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
