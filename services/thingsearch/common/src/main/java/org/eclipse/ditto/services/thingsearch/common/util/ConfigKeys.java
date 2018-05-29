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

    private static final String MONITORING_PREFIX = MONGO_CONFIG_PREFIX + "monitoring.";
    /**
     * Whether all commands should be monitored and reported with Kamon.
     */
    public static final String MONITORING_COMMANDS_ENABLED = MONITORING_PREFIX + "commands";
    /**
     * Whether connection pool statistics should be reported with Kamon.
     */
    public static final String MONITORING_CONNECTION_POOL_ENABLED = MONITORING_PREFIX + "connection-pool";

    private static final String ENABLED_SUFFIX = "enabled";

    /**
     * Key of the index-initialization enabled configuration.
     */
    public static final String INDEX_INITIALIZATION_ENABLED = SEARCH_PREFIX + "index-initialization." + ENABLED_SUFFIX;

    /**
     * Controls whether thing and policy event processing should be active or not.
     */
    public static final String EVENT_PROCESSING_ACTIVE = SEARCH_UPDATER_PREFIX + "event-processing.active";

    /**
     * Controls maximum number of events to update in a bulk.
     */
    public static final String MAX_BULK_SIZE = SEARCH_UPDATER_PREFIX + "max-bulk-size";

    /**
     * Controls whether thing and policy cache-updates should be active or not.
     */
    public static final String CACHE_UPDATES_ACTIVE = SEARCH_UPDATER_PREFIX + "cache-updates.active";

    /**
     * The interval which defines how long a thing updater is considered active. When not active, the corresponding
     * actor can be stopped.
     */
    public static final String THINGS_ACTIVITY_CHECK_INTERVAL = SEARCH_UPDATER_PREFIX +
            "activity-check-interval";

    private static final String SYNC_PREFIX = SEARCH_UPDATER_PREFIX + "sync.";

    private static final String SYNC_THINGS_PREFIX = SYNC_PREFIX + "things.";

    /**
     * Things-Sync: Controls whether the sync should be active or not.
     */
    public static final String THINGS_SYNCER_ACTIVE = SYNC_THINGS_PREFIX + "active";

    /**
     * Things-Sync: The syncer makes sure that all requested stream elements have at least an age of this offset, e.g by
     * triggering a stream at a later time.
     */
    public static final String THINGS_SYNCER_START_OFFSET = SYNC_THINGS_PREFIX + "start-offset";

    /**
     * Things-Sync: The duration from now to somewhere in the past for which stream elements are requested if sync
     * has never been run before - otherwise sync is started where the last run finished.
     */
    public static final String THINGS_SYNCER_INITIAL_START_OFFSET = SYNC_THINGS_PREFIX + "initial-start-offset";

    /**
     * Things-Sync: The interval for the query restricting the stream (i.e. the difference between query-start and
     * query-end). This query-interval is used for <strong>all</strong> queries, but the interval of stream-starts
     * varies depending on the stream load.
     */
    public static final String THINGS_SYNCER_STREAM_INTERVAL = SYNC_THINGS_PREFIX + "stream-interval";

    /**
     * Things-Sync: if a query-start is more than this offset in the past, a warning will be logged.
     */
    public static final String THINGS_SYNCER_OUTDATED_WARNING_OFFSET = SYNC_THINGS_PREFIX + "outdated-warning-offset";

    /**
     * Things-Sync: if a query-start is more than this offset in the past, an errir will be logged and health
     * endpoint shows "DOWN".
     */
    public static final String THINGS_SYNCER_OUTDATED_ERROR_OFFSET = SYNC_THINGS_PREFIX + "outdated-error-offset";

    /**
     * Things-Sync: The maximum idle time of the syncer (as a Duration).
     */
    public static final String THINGS_SYNCER_MAX_IDLE_TIME = SYNC_THINGS_PREFIX + "max-idle-time";

    /**
     * Things-Sync: Timeout at streaming actor (server) side.
     */
    public static final String THINGS_SYNCER_STREAMING_ACTOR_TIMEOUT = SYNC_THINGS_PREFIX + "streaming-actor-timeout";

    /**
     * Things-Sync: The elements to be streamed per batch by the sync process.
     */
    public static final String THINGS_SYNCER_ELEMENTS_STREAMED_PER_BATCH = SYNC_THINGS_PREFIX +
            "elements-streamed-per-batch";

    private static final String SYNC_POLICIES_PREFIX = SYNC_PREFIX + "policies.";

    /**
     * Policies-Sync: Controls whether the sync should be active or not.
     */
    public static final String POLICIES_SYNCER_ACTIVE = SYNC_POLICIES_PREFIX + "active";

    /**
     * Policies-Sync: The syncer makes sure that all requested stream elements have at least an age of this offset,
     * e.g by triggering a stream at a later time.
     */
    public static final String POLICIES_SYNCER_START_OFFSET = SYNC_POLICIES_PREFIX + "start-offset";

    /**
     * Policies-Sync: The duration from now to somewhere in the past for which stream elements are requested if sync
     * has never been run before - otherwise sync is started where the last run finished.
     */
    public static final String POLICIES_SYNCER_INITIAL_START_OFFSET = SYNC_POLICIES_PREFIX + "initial-start-offset";

    /**
     * Policies-Sync: The interval for the query restricting the stream (i.e. the difference between query-start and
     * query-end). This query-interval is used for <strong>all</strong> queries, but the interval of stream-starts
     * varies depending on the stream load.
     */
    public static final String POLICIES_SYNCER_STREAM_INTERVAL = SYNC_POLICIES_PREFIX + "stream-interval";

    /**
     * Policies-Sync: if a query-start is more than this offset in the past, a warning will be logged.
     */
    public static final String POLICIES_SYNCER_OUTDATED_WARNING_OFFSET =
            SYNC_POLICIES_PREFIX + "outdated-warning-offset";

    /**
     * Policies-Sync: if a query-start is more than this offset in the past, an error will be logged and the health
     * endpoint shows "DOWN".
     */
    public static final String POLICIES_SYNCER_OUTDATED_ERROR_OFFSET =
            SYNC_POLICIES_PREFIX + "outdated-error-offset";

    /**
     * Policies-Sync: The maximum idle time of the syncer (as a Duration).
     */
    public static final String POLICIES_SYNCER_MAX_IDLE_TIME = SYNC_POLICIES_PREFIX + "max-idle-time";

    /**
     * Policies-Sync: Timeout at streaming actor (server) side.
     */
    public static final String POLICIES_SYNCER_STREAMING_ACTOR_TIMEOUT =
            SYNC_POLICIES_PREFIX + "streaming-actor-timeout";

    /**
     * Policies-Sync: The elements to be streamed per batch by the sync process.
     */
    public static final String POLICIES_SYNCER_ELEMENTS_STREAMED_PER_BATCH = SYNC_POLICIES_PREFIX +
            "elements-streamed-per-batch";

    /*
     * This class is not designed for instantiation.
     */
    private ConfigKeys() {
        // no-op
    }
}
