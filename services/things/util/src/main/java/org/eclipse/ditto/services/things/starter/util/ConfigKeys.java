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

    private static final String ENABLED_SUFFIX = "enabled";

    public static final class Http {

        private static final String PREFIX = THINGS_PREFIX + "http.";

        /**
         * Key of the hostname value of a HTTP service.
         */
        public static final String HOSTNAME = PREFIX + "hostname";

        /**
         * Key of the port number value of a HTTP service.
         */
        public static final String PORT = PREFIX + "port";

        private Http() {
            throw new AssertionError();
        }

    }

    public static final class Cluster {

        private static final String PREFIX = THINGS_PREFIX + "cluster.";

        private static final String MAJORITY_CHECK_PREFIX = PREFIX + "majority-check.";

        /**
         * Key of the majority check delay.
         */
        public static final String MAJORITY_CHECK_DELAY = MAJORITY_CHECK_PREFIX + "delay";

        /**
         * Key of the majority check enabled configuration.
         */
        public static final String MAJORITY_CHECK_ENABLED = MAJORITY_CHECK_PREFIX + ENABLED_SUFFIX;

        /**
         * Key of the how many shards should be used in the cluster.
         */
        public static final String NUMBER_OF_SHARDS = PREFIX + "number-of-shards";

        private Cluster() {
            throw new AssertionError();
        }

    }

    public static final class WebSocket {

        private static final String PREFIX = THINGS_PREFIX + "websocket.";

        /**
         * Key of the web socket publisher backpressure config.
         */
        public static final String PUBLISHER_BACKPRESSURE = PREFIX + "publisher.backpressure-buffer-size";

        /**
         * Key of the web socket subscriber backpressure config.
         */
        public static final String SOCKET_SUBSCRIBER_BACKPRESSURE = PREFIX + "subscriber.backpressure-queue-size";

        private WebSocket() {
            throw new AssertionError();
        }

    }

    public static final class HealthCheck {

        private static final String PREFIX = THINGS_PREFIX + "health-check.";

        private static final String PERSISTENCE_PREFIX = PREFIX + "persistence.";

        /**
         * The timeout of the health check for persistence. If the persistence takes longer than that to respond, it is
         * considered "DOWN".
         */
        public static final String PERSISTENCE_TIMEOUT = PERSISTENCE_PREFIX + "timeout";

        /**
         * Whether the health check for persistence should be enabled or not.
         */
        public static final String PERSISTENCE_ENABLED = PERSISTENCE_PREFIX + ENABLED_SUFFIX;

        /**
         * The interval of the health check.
         */
        public static final String INTERVAL = PREFIX + "interval";

        /**
         * Whether the health check should be enabled (globally) or not.
         */
        public static final String ENABLED = PREFIX + ENABLED_SUFFIX;

        private HealthCheck() {
            throw new AssertionError();
        }

    }

    public static final class StatsD {

        private static final String PREFIX = THINGS_PREFIX + "statsd.";

        /**
         * The StatsD hostname used for sending metrics to.
         */
        public static final String HOSTNAME = PREFIX + "hostname";

        /**
         * The StatsD port used for sending metrics to.
         */
        public static final String PORT = PREFIX + "port";

        private StatsD() {
            throw new AssertionError();
        }

    }

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
