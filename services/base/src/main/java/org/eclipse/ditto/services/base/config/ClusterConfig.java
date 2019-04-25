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
package org.eclipse.ditto.services.base.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for the Ditto cluster.
 * <p>
 * Java serialization is supported for {@code ClusterConfig}.
 * </p>
 */
@Immutable
public interface ClusterConfig {

    /**
     * Returns the number of shards in a cluster.
     *
     * @return the number of shards.
     */
    int getNumberOfShards();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code ClusterConfig}.
     */
    enum ClusterConfigValue implements KnownConfigValue {

        /**
         * The number of shards in a cluster.
         */
        NUMBER_OF_SHARDS("number-of-shards", 30);

        private final String path;
        private final Object defaultValue;

        private ClusterConfigValue(final String thePath, final Object theDefaultValue) {
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
