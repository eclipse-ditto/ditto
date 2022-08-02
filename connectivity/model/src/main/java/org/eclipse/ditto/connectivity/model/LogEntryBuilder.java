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
package org.eclipse.ditto.connectivity.model;

import java.time.Instant;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.things.model.ThingId;

/**
 * A mutable builder for a {@link LogEntry} with a fluent API.
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
     * @param entityId entity ID if the log can be correlated to a known entity (e.g. a Thing), empty otherwise.
     * @return this builder to allow method chaining.
     */
    LogEntryBuilder entityId(@Nullable EntityId entityId);

    /**
     * @param thingId Thing ID if the log can be correlated to a known Thing, empty otherwise.
     * @return this builder to allow method chaining.
     * @deprecated replaced by {@link #entityId(org.eclipse.ditto.base.model.entity.id.EntityId)}
     */
    @Deprecated
    default LogEntryBuilder thingId(@Nullable ThingId thingId) {
        return entityId(thingId);
    }

    /**
     * Builds a new {@link LogEntry}.
     * @return the new {@link LogEntry}.
     */
    LogEntry build();

}
