/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.akka.streaming;

import java.time.Instant;
import java.util.Optional;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Defines operations for managing metadata of streams.
 */
public interface TimestampPersistence {

    /**
     * Updates the timestamp in the persistence..
     *
     * @param timestamp The timestamp.
     * @return a {@link Source} holding the publisher to execute the operation.
     */
    Source<NotUsed, NotUsed> setTimestamp(Instant timestamp);

    /**
     * Retrieve the timestamp in the persistence.
     *
     * @return a {@link Source} of the {@link Instant} stored in the persistence.
     */
    Source<Optional<Instant>, NotUsed> getTimestampAsync();
}
