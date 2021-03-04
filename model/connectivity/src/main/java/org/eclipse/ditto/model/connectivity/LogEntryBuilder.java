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
package org.eclipse.ditto.model.connectivity;

import java.time.Instant;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.things.ThingId;

/**
 * A mutable builder for a {@link org.eclipse.ditto.model.connectivity.LogEntry} with a fluent API.
 */
public interface LogEntryBuilder {

    /**
     * @param correlationId  correlation ID that is associated with the log entry.
     * @return this builder to allow method chaining.
     */
    LogEntryBuilder correlationId(String correlationId);

    /**
     * @param timestamp  timestamp of the log entry.
     * @return this builder to allow method chaining.
     */
    LogEntryBuilder timestamp(Instant timestamp);

    /**
     * @param logCategory  category of the log entry.
     * @return this builder to allow method chaining.
     */
    LogEntryBuilder logCategory(LogCategory logCategory);

    /**
     * @param logType  type of the log entry.
     * @return this builder to allow method chaining.
     */
    LogEntryBuilder logType(LogType logType);

    /**
     * @param logLevel  the log level.
     * @return this builder to allow method chaining.
     */
    LogEntryBuilder logLevel(LogLevel logLevel);

    /**
     * @param message  the log message.
     * @return this builder to allow method chaining.
     */
    LogEntryBuilder message(String message);

    /**
     * @param address if the log can be correlated to a known source or target, empty otherwise.
     * @return this builder to allow method chaining.
     */
    LogEntryBuilder address(@Nullable String address);

    /**
     * @param thingId  thing ID if the log can be correlated to a known Thing, empty otherwise.
     * @return this builder to allow method chaining.
     */
    LogEntryBuilder thingId(@Nullable ThingId thingId);

    /**
     * Builds a new {@link org.eclipse.ditto.model.connectivity.LogEntry}.
     * @return the new {@link org.eclipse.ditto.model.connectivity.LogEntry}.
     */
    LogEntry build();

}
