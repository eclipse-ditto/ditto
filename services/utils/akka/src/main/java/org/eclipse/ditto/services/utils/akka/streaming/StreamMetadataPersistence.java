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
package org.eclipse.ditto.services.utils.akka.streaming;

import java.time.Instant;
import java.util.Optional;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Defines operations for managing metadata of streams.
 */
public interface StreamMetadataPersistence {

    /**
     * Updates the end timestamp of the last successful stream.
     *
     * @param timestamp The timestamp.
     * @return a {@link Source} holding the publisher to execute the operation.
     */
    Source<NotUsed, NotUsed> updateLastSuccessfulStreamEnd(Instant timestamp);

    /**
     * <strong>Blocking:</strong> Retrieves the end timestamp of the last successful stream.
     *
     * @return An {@link java.util.Optional} of the {@link Instant} of the last successful stream.
     * Optional will be empty if a timestamp has not yet been persisted.
     */
    Optional<Instant> retrieveLastSuccessfulStreamEnd();
}
