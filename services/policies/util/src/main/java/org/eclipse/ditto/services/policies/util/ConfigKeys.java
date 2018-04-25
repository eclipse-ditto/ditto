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
package org.eclipse.ditto.services.policies.util;

/**
 * This class encloses everything regarding configuration keys.
 */
public final class ConfigKeys {

    /**
     * Key of the uri for mongodb.
     */
    public static final String MONGO_URI = "akka.contrib.persistence.mongodb.mongo.mongouri";

    private static final String DITTO_PREFIX = "ditto.";

    private static final String POLICIES_PREFIX = DITTO_PREFIX + "policies.";

    private static final String POLICIES_TAGS_PREFIX = POLICIES_PREFIX + "tags.";

    /**
     * The size of the cache used for streaming Policy Tags (each stream has its own cache).
     */
    public static final String POLICIES_TAGS_STREAMING_CACHE_SIZE = POLICIES_TAGS_PREFIX +
            "streaming-cache-size";

    private static final String HTTP_PREFIX = POLICIES_PREFIX + "http.";

    /**
     * Key of the hostname value of a HTTP service.
     */
    public static final String HTTP_HOSTNAME = HTTP_PREFIX + "hostname";

    /**
     * Key of the port number value of a HTTP service.
     */
    public static final String HTTP_PORT = HTTP_PREFIX + "port";
    private static final String ENABLED_SUFFIX = "enabled";

    public static final class Cluster {

        private static final String PREFIX = POLICIES_PREFIX + "cluster.";

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

    public static final class HealthCheck {

        private static final String PREFIX = POLICIES_PREFIX + "health-check.";

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
        public static final String CHECK_INTERVAL = PREFIX + "interval";

        /**
         * Whether the health check should be enabled (globally) or not.
         */
        public static final String ENABLED = PREFIX + ENABLED_SUFFIX;

        private HealthCheck() {
            throw new AssertionError();
        }

    }

    public static final class StatsD {

        private static final String PREFIX = POLICIES_PREFIX + "statsd.";

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

    public static final class Policy {

        private static final String PREFIX = POLICIES_PREFIX + "policy.";

        private static final String SUPERVISOR_PREFIX = PREFIX + "supervisor.";

        private static final String SUPERVISOR_EXPONENTIAL_BACKOFF = SUPERVISOR_PREFIX + "exponential-backoff.";

        private static final String POLICY_EVENTS_PREFIX = PREFIX + "events.";

        private static final String POLICY_SNAPSHOT_PREFIX = PREFIX + "snapshot.";

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
        public static final String EVENTS_DELETE_OLD = POLICY_EVENTS_PREFIX + "delete-old";

        /**
         * Whether to delete old Snapshot or not when a Snapshot is taken.
         */
        public static final String SNAPSHOT_DELETE_OLD = POLICY_SNAPSHOT_PREFIX + "delete-old";

        /**
         * Every amount of changes (configured by this key), this Actor will create a snapshot of the Policy.
         */
        public static final String SNAPSHOT_THRESHOLD = POLICY_SNAPSHOT_PREFIX + "threshold";

        /**
         * The interval when to do snapshot for a Policy which had changes to it.
         */
        public static final String SNAPSHOT_INTERVAL = POLICY_SNAPSHOT_PREFIX + "interval";

        /**
         * How long to keep deleted Policies in memory.
         */
        public static final String ACTIVITY_CHECK_INTERVAL = PREFIX + "activity.check.interval";

        /**
         * How long to keep deleted Policies in memory.
         */
        public static final String ACTIVITY_CHECK_DELETED_INTERVAL = PREFIX + "activity.check.deleted.interval";

        private Policy() {
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
