/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.api;

/**
 * Describes the reason why a thing is being updated in the search index.
 *
 * @since 2.3.0
 */
public enum UpdateReason {

    /**
     * The policy referenced by a thing was updated.
     */
    POLICY_UPDATE,
    /**
     * The thing is indexed as part of a manual re-indexing.
     */
    MANUAL_REINDEXING,
    /**
     * The thing was updated.
     */
    THING_UPDATE,
    /**
     * The thing is indexed as part of the automatic background sync.
     */
    BACKGROUND_SYNC,
    /**
     * Reason not known.
     */
    UNKNOWN,
    /**
     * A search update failed and the update is retried.
     */
    RETRY,

    /**
     * A force update is executed after actor startup to ensure consistency against the database.
     */
    FORCE_UPDATE_AFTER_START
}
