/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

    private static final String DELETION_PREFIX = SEARCH_PREFIX + "deletion.";

    /**
     * Key configuring the age (as Duration) after which marked as deleted Things are physically deleted from search
     * index.
     */
    public static final String DELETION_AGE = DELETION_PREFIX + "deletion-age";

    /**
     * Whether or not the deletion of marked as deleted Things from the search index is enabled.
     */
    public static final String DELETION_ENABLED = DELETION_PREFIX + "enabled";

    /**
     * Key configuring the interval (as Duration) when marked as deleted Things should be deleted (e.g. once a day).
     */
    public static final String DELETION_RUN_INTERVAL = DELETION_PREFIX + "run-interval";

    /**
     * Key configuring the hour (as int) of the first time the deletion should be triggered. After that the deletion is
     * done each {@link #DELETION_RUN_INTERVAL}.
     */
    public static final String DELETION_FIRST_INTERVAL_HOUR = DELETION_PREFIX + "first-interval-hour";

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

    /**
     * Things-Sync: Path to the stream consumer config.
     */
    public static final String SYNC_THINGS = SYNC_PREFIX + "things";

    private static final String SYNC_THINGS_PREFIX = SYNC_THINGS + ".";

    /**
     * Things-Sync: Controls whether the sync should be active or not.
     */
    public static final String THINGS_SYNCER_ACTIVE = SYNC_THINGS_PREFIX + "active";

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
     * Policies-Sync: Path to the stream consumer config.
     */
    public static final String SYNC_POLICIES = SYNC_PREFIX + "policies";

    private static final String SYNC_POLICIES_PREFIX = SYNC_POLICIES + ".";

    /**
     * Policies-Sync: Controls whether the sync should be active or not.
     */
    public static final String POLICIES_SYNCER_ACTIVE = SYNC_POLICIES_PREFIX + "active";

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


    /*
     * This class is not designed for instantiation.
     */
    private ConfigKeys() {
        // no-op
    }
}
