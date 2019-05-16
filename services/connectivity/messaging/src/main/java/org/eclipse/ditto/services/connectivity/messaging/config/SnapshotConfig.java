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
package org.eclipse.ditto.services.connectivity.messaging.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for the snapshotting behaviour.
 * <p>
 * Java serialization is supported for {@code SnapshotConfig}.
 * </p>
 */
@Immutable
public interface SnapshotConfig {

    /**
     * Returns the amount of changes after which a snapshot of the connection status is created.
     *
     * @return the snapshot threshold.
     */
    int getThreshold();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code SnapshotConfig}.
     */
    enum SnapshotConfigValue implements KnownConfigValue {

        /**
         * The amount of changes after which a snapshot of the connection status is created.
         */
        THRESHOLD("threshold", 10);

        private final String path;
        private final Object defaultValue;

        private SnapshotConfigValue(final String thePath, final Object theDefaultValue) {
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
