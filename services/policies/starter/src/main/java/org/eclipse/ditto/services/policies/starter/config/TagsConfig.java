/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.policies.starter.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides the configuration settings of the policies' tags.
 * <p>
 * Java serialization is supported for {@code TagsConfig}.
 * </p>
 */
@Immutable
public interface TagsConfig {

    /**
     * Returns the size of the cache used for streaming Policy Tags (each stream has its own cache).
     *
     * @return the size.
     */
    int getStreamingCacheSize();

    /**
     * An enumeration of the known config path expressions and their associated default values for {@code TagsConfig}.
     */
    enum TagsConfigValue implements KnownConfigValue {

        /**
         * The size of the cache used for streaming Policy Tags (each stream has its own cache).
         */
        STREAMING_CACHE_SIZE("streaming-cache-size", 1_000);

        private final String path;
        private final Object defaultValue;

        private TagsConfigValue(final String thePath, final Object theDefaultValue) {
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
