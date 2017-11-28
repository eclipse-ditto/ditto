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
package org.eclipse.ditto.services.thingsearch.persistence.write;

import java.time.Instant;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Defines operations for managing synchronization state.
 */
public interface ThingsSearchSyncPersistence {

    /**
     * Initializes the persistence.
     */
    void init();

    /**
     * Updates the last successful full search sync timestamp in the database.
     *
     * @param timestamp The timestamp.
     * @return a {@link Source} holding the publisher to execute the operation.
     */
    Source<NotUsed, NotUsed> updateLastSuccessfulSyncTimestamp(Instant timestamp);

    /**
     * Retrieves the last successful full search sync timestamp from the database or the provided {@code
     * defaultTimestamp}, if a timestamp has not yet been persisted.
     *
     * @param defaultTimestamp The default timestamp to be returned if a timestamp has not yet been persisted.
     * @return a {@link Source} holding the publisher to execute the operation.
     */
    Source<Instant, NotUsed> retrieveLastSuccessfulSyncTimestamp(Instant defaultTimestamp);
}
