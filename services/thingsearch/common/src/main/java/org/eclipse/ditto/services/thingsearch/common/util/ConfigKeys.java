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
package org.eclipse.ditto.services.thingsearch.common.util;

/**
 * This class encloses everything regarding configuration keys.
 */
public final class ConfigKeys {

    /**
     * Name of things-search service.
     */
    public static final String SERVICE_NAME = "things-search";

    /**
     * Role of things-search service.
     */
    public static final String SEARCH_ROLE = SERVICE_NAME;

    private static final String DITTO_PREFIX = "ditto.";
    private static final String SEARCH_PREFIX = DITTO_PREFIX + SEARCH_ROLE + ".";
    private static final String SEARCH_UPDATER_PREFIX = SEARCH_PREFIX + "updater.";
    private static final String CLUSTER_PREFIX = SEARCH_PREFIX + "cluster.";
    private static final String CLUSTER_MAJORITY_CHECK_PREFIX = CLUSTER_PREFIX + "majority-check.";
    private static final String STATSD_PREFIX = SEARCH_PREFIX + "statsd.";
    /**
     * Prefix for mongoDB config
     */
    private static final String MONGO_CONFIG_PREFIX = SEARCH_PREFIX + "mongodb.";
    /**
     * Prefix for circuit breaker config.
     */
    private static final String MONGO_CIRCUIT_BREAKER_CONFIG_PREFIX = MONGO_CONFIG_PREFIX + "breaker.";
    /**
     * Max retries config for circuit breaker.
     */
    public static final String MONGO_CIRCUIT_BREAKER_FAILURES = MONGO_CIRCUIT_BREAKER_CONFIG_PREFIX + "maxFailures";
    /**
     * Call timeout config for circuit breaker.
     */
    public static final String MONGO_CIRCUIT_BREAKER_TIMEOUT_CALL =
            MONGO_CIRCUIT_BREAKER_CONFIG_PREFIX + "timeout.call";
    /**
     * Reset timeout for circuit breaker.
     */
    public static final String MONGO_CIRCUIT_BREAKER_TIMEOUT_RESET =
            MONGO_CIRCUIT_BREAKER_CONFIG_PREFIX + "timeout.reset";

    private static final String MONGO_CONNECTION_POOL_PREFIX = MONGO_CONFIG_PREFIX + "connection-pool.";

    private static final String ENABLED_SUFFIX = "enabled";

    /**
     * Key of the "max-size" of the connection pool.
     */
    public static final String MONGO_CONNECTION_POOL_MAX_SIZE = MONGO_CONNECTION_POOL_PREFIX + "max-size";

    /**
     * Key of the "max-wait-time" of the connection pool.
     */
    public static final String MONGO_CONNECTION_POOL_MAX_WAIT_TIME = MONGO_CONNECTION_POOL_PREFIX + "max-wait-time";

    /**
     * Key of the "max-wait-queue-size" of the connection pool.
     */
    public static final String MONGO_CONNECTION_POOL_MAX_WAIT_QUEUE_SIZE = MONGO_CONNECTION_POOL_PREFIX + "max-wait-queue-size";

    /**
     * Controls whether thing event processing should be active or not.
     */
    public static final String THINGS_EVENT_PROCESSING_ACTIVE = SEARCH_UPDATER_PREFIX + "event-processing.active";

    /**
     * The interval which defines how long a thing updater is considered active. When not active, the corresponding
     * actor can be stopped.
     */
    public static final String THINGS_ACTIVITY_CHECK_INTERVAL = SEARCH_UPDATER_PREFIX +
            "activity-check-interval";
    /**
     * Key of the how many shards should be used in the cluster.
     */
    public static final String CLUSTER_NUMBER_OF_SHARDS = CLUSTER_PREFIX + "number-of-shards";

    /**
     * Key of the majority check enabled configuration.
     */
    public static final String CLUSTER_MAJORITY_CHECK_ENABLED = CLUSTER_MAJORITY_CHECK_PREFIX + ENABLED_SUFFIX;
    /**
     * Key of the majority check delay.
     */
    public static final String CLUSTER_MAJORITY_CHECK_DELAY = CLUSTER_MAJORITY_CHECK_PREFIX + "delay";
    private static final String SYNC_PREFIX = SEARCH_UPDATER_PREFIX + "sync.";
    /**
     * The syncing period within which there are requested updated things - only used in case no successful sync has
     * been run yet, otherwise sync is started where the last run finished.
     */
    public static final String THINGS_SYNCER_PERIOD = SYNC_PREFIX + "period";
    /**
     * The offset for the syncing of things.
     */
    public static final String THINGS_SYNCER_OFFSET = SYNC_PREFIX + "offset";
    /**
     * The maximum idle time of the syncer (as a Duration).
     */
    public static final String THINGS_SYNCER_MAX_IDLE_TIME = SYNC_PREFIX + "max-idle-time";

    /**
     * The elements to be streamed per second by the sync process.
     */
    public static final String THINGS_SYNCER_ELEMENTS_STREAMED_PER_SECOND = SYNC_PREFIX +
            "elements-streamed-per-second";
    /**
     * Controls whether the sync should be active or not.
     */
    public static final String THINGS_SYNCER_ACTIVE = SYNC_PREFIX + "active";

    private static final String HTTP_PREFIX = SEARCH_PREFIX + "http.";
    /**
     * Key of the hostname value of a HTTP service.
     */
    public static final String HTTP_HOSTNAME = HTTP_PREFIX + "hostname";
    /**
     * Key of the port number value of a HTTP service.
     */
    public static final String HTTP_PORT = HTTP_PREFIX + "port";
    private static final String HEALTH_CHECK_PREFIX = SEARCH_PREFIX + "health-check.";
    /**
     * Whether the health check should be enabled (globally) or not.
     */
    public static final String HEALTH_CHECK_ENABLED = HEALTH_CHECK_PREFIX + ENABLED_SUFFIX;
    /**
     * The interval of the health check.
     */
    public static final String HEALTH_CHECK_INTERVAL = HEALTH_CHECK_PREFIX + "interval";
    private static final String HEALTH_CHECK_PERSISTENCE_PREFIX = HEALTH_CHECK_PREFIX + "persistence.";
    /**
     * Whether the health check for persistence should be enabled or not.
     */
    public static final String HEALTH_CHECK_PERSISTENCE_ENABLED = HEALTH_CHECK_PERSISTENCE_PREFIX + ENABLED_SUFFIX;
    /**
     * The timeout of the health check for persistence. If the persistence takes longer than that to respond, it is
     * considered "DOWN".
     */
    public static final String HEALTH_CHECK_PERSISTENCE_TIMEOUT = HEALTH_CHECK_PERSISTENCE_PREFIX + "timeout";
    /**
     * The StatsD hostname used for sending metrics to.
     */
    public static final String STATSD_HOSTNAME = STATSD_PREFIX + "hostname";
    /**
     * The StatsD port used for sending metrics to.
     */
    public static final String STATSD_PORT = STATSD_PREFIX + "port";

    /*
     * This class is not designed for instantiation.
     */
    private ConfigKeys() {
        // no-op
    }
}
