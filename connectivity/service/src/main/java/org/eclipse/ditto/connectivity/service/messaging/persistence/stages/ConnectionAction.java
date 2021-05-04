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
package org.eclipse.ditto.connectivity.service.messaging.persistence.stages;

/**
 * Actions for the connection actor to undertake.
 */
public enum ConnectionAction {

    /**
     * Tell client actors to test the connection.
     */
    TEST_CONNECTION,

    /**
     * Apply an event without persisting it.
     */
    APPLY_EVENT,

    /**
     * Send the response to the original sender.
     */
    SEND_RESPONSE,

    /**
     * Ask parent to stop self.
     */
    PASSIVATE,

    /**
     * Tell client actors to open the connection but do not abort on error.
     */
    OPEN_CONNECTION_IGNORE_ERRORS,

    /**
     * Tell client actors to open the connection.
     */
    OPEN_CONNECTION,

    /**
     * Tell client actors to close the connection.
     */
    CLOSE_CONNECTION,

    /**
     * Stop client actors.
     */
    STOP_CLIENT_ACTORS,

    /**
     * Write an event into the journal.
     */
    PERSIST_AND_APPLY_EVENT,

    /**
     * Become created.
     */
    BECOME_CREATED,

    /**
     * Become deleted.
     */
    BECOME_DELETED,

    /**
     * Update pubsub topics.
     */
    UPDATE_SUBSCRIPTIONS,

    /**
     * Forward command to client actors without waiting for reply.
     */
    BROADCAST_TO_CLIENT_ACTORS_IF_STARTED,

    /**
     * Retrieve connection logs.
     */
    RETRIEVE_CONNECTION_LOGS,

    /**
     * Retrieve connection status.
     */
    RETRIEVE_CONNECTION_STATUS,

    /**
     * Retrieve connection metrics.
     */
    RETRIEVE_CONNECTION_METRICS,

    /**
     * Enable logging.
     */
    ENABLE_LOGGING,

    /**
     * Disable logging.
     */
    DISABLE_LOGGING
}
