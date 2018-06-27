/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.things.starter.util;

/**
 * This class encloses everything regarding configuration keys.
 */
public final class ConfigKeys {

    /**
     * Key of the uri for mongodb.
     */
    public static final String MONGO_URI = "akka.contrib.persistence.mongodb.mongo.mongouri";

    private static final String THINGS_PREFIX = "ditto.things.";

    /**
     * Whether to log all messages received by thing persistence actors.
     */
    public static final String THINGS_LOG_INCOMING_MESSAGES = THINGS_PREFIX + "log-incoming-messages";

    private static final String THINGS_TAGS_PREFIX = THINGS_PREFIX + "tags.";

    /**
     * The size of the cache used for streaming Thing Tags (each stream has its own cache).
     */
    public static final String THINGS_TAGS_STREAMING_CACHE_SIZE = THINGS_TAGS_PREFIX +
            "streaming-cache-size";

    public static final class Thing {

        private static final String PREFIX = THINGS_PREFIX + "thing.";

        private static final String SUPERVISOR_PREFIX = PREFIX + "supervisor.";

        private static final String SUPERVISOR_EXPONENTIAL_BACKOFF = SUPERVISOR_PREFIX + "exponential-backoff.";

        private static final String EVENTS_PREFIX = PREFIX + "events.";

        private static final String SNAPSHOT_PREFIX = PREFIX + "snapshot.";

        /**
         * The random factor of the exponential back-off strategy.
         */
        public static final String
                SUPERVISOR_EXPONENTIAL_BACKOFF_RANDOM_FACTOR = SUPERVISOR_EXPONENTIAL_BACKOFF + "random-factor";

        /**
         * The maximal exponential back-off duration.
         */
        public static final String SUPERVISOR_EXPONENTIAL_BACKOFF_MAX = SUPERVISOR_EXPONENTIAL_BACKOFF + "max";

        /**
         * The minimal exponential back-off duration.
         */
        public static final String SUPERVISOR_EXPONENTIAL_BACKOFF_MIN = SUPERVISOR_EXPONENTIAL_BACKOFF + "min";

        /**
         * Whether to delete old Events or not when a Snapshot is taken.
         */
        public static final String EVENTS_DELETE_OLD = EVENTS_PREFIX + "delete-old";

        /**
         * Whether to delete old Snapshot or not when a Snapshot is taken.
         */
        public static final String SNAPSHOT_DELETE_OLD = SNAPSHOT_PREFIX + "delete-old";

        /**
         * Every amount of changes (configured by this key), this Actor will create a snapshot of the thing.
         */
        public static final String SNAPSHOT_THRESHOLD = SNAPSHOT_PREFIX + "threshold";
        
        /**
         * The interval when to do snapshot for a Thing which had changes to it.
         */
        public static final String SNAPSHOT_INTERVAL = SNAPSHOT_PREFIX + "interval";

        /**
         * The activity interval for things with lifecycle state deleted.
         *
         * @see #ACTIVITY_CHECK_INTERVAL
         */
        public static final String ACTIVITY_CHECK_DELETED_INTERVAL = PREFIX + "activity.check.deleted.interval";

        /**
         * Every interval of this duration (configured by this key), this Actor checks if there was activity "with it"
         * (e. g. reads/writes). If there was none, the Actor shuts itself down in order to free up resources.
         */
        public static final String ACTIVITY_CHECK_INTERVAL = PREFIX + "activity.check.interval";

        private Thing() {
            throw new AssertionError();
        }

    }

    /*
     * This class is not designed for instantiation.
     */
    private ConfigKeys() {
        throw new AssertionError();
    }

}
