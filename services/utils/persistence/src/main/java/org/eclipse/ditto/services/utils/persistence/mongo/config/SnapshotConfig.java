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
package org.eclipse.ditto.services.utils.persistence.mongo.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for the handling entity snapshots.
 */
@Immutable
public interface SnapshotConfig {

    /**
     * Returns the interval when to do snapshot for an entity which had changes to it.
     *
     * @return the interval.
     */
    Duration getInterval();

    /**
     * Returns the threshold after how many changes to an entity to do a snapshot.
     *
     * @return the threshold.
     */
    long getThreshold();

    /**
     * Indicates whether to delete old snapshot when taking a snapshot.
     *
     * @return {@code true} if the old snapshot should be deleted, {@code false} else.
     */
    boolean isDeleteOldSnapshot();

    /**
     * Indicates whether to delete old events when taking a snapshot.
     *
     * @return {@code true} if the old events should be deleted, {@code false} else.
     */
    boolean isDeleteOldEvents();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code SnapshotConfig}.
     */
    enum SnapshotConfigValue implements KnownConfigValue {

        /**
         * The interval when to do snapshot for an entity which had changes to it.
         */
        INTERVAL("interval", Duration.ofMinutes(15L)),

        /**
         * The threshold after how many changes to an entity to do a snapshot.
         */
        THRESHOLD("threshold", 500L),

        /**
         * Determines whether to delete old snapshot when taking a snapshot.
         */
        DELETE_OLD_SNAPSHOT("delete-old-snapshot", false),

        /**
         * Determines whether to delete old events when taking a snapshot.
         */
        DELETE_OLD_EVENTS("delete-old-events", false);

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
