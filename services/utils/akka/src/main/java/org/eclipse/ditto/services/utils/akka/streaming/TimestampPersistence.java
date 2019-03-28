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
