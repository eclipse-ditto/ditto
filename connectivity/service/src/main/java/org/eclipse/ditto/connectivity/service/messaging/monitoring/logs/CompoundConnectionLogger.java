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
package org.eclipse.ditto.connectivity.service.messaging.monitoring.logs;

import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;

/**
 * Implementation of {@link ConnectionLogger} that combines several other instances of {@link ConnectionLogger} and
 * delegates, e.g. all log writing, to all of those instances.
 */
final class CompoundConnectionLogger implements ConnectionLogger, MuteableConnectionLogger {

    private final Collection<ConnectionLogger> connectionLoggers;

    CompoundConnectionLogger(final Collection<ConnectionLogger> connectionLoggers) {
        this.connectionLoggers = connectionLoggers;
    }

    @Override
    public Collection<LogEntry> getLogs() {
        return connectionLoggers.stream()
                .map(ConnectionLogger::getLogs)
                .flatMap(Collection::stream)
                .toList();
    }

    @Override
    public void success(final ConnectionMonitor.InfoProvider infoProvider) {
        connectionLoggers.forEach(logger -> logger.success(infoProvider));
    }

    @Override
    public void success(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final Object... messageArguments) {
        connectionLoggers.forEach(logger -> logger.success(infoProvider, message, messageArguments));
    }

    @Override
    public void failure(final ConnectionMonitor.InfoProvider infoProvider,
            @Nullable final DittoRuntimeException exception) {
        connectionLoggers.forEach(logger -> logger.failure(infoProvider, exception));
    }

    @Override
    public void failure(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final Object... messageArguments) {
        connectionLoggers.forEach(logger -> logger.failure(infoProvider, message, messageArguments));
    }

    @Override
    public void exception(final ConnectionMonitor.InfoProvider infoProvider, @Nullable final Throwable exception) {
        connectionLoggers.forEach(logger -> logger.exception(infoProvider, exception));
    }

    @Override
    public void exception(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final Object... messageArguments) {
        connectionLoggers.forEach(logger -> logger.exception(infoProvider, message, messageArguments));
    }

    @Override
    public void logEntry(final LogEntry logEntry) {
        connectionLoggers.forEach(logger -> logger.logEntry(logEntry));
    }

    @Override
    public void clear() {
        connectionLoggers.forEach(ConnectionLogger::clear);
    }

    @Override
    public void mute() {
        connectionLoggers.stream()
                .filter(MuteableConnectionLogger.class::isInstance)
                .map(MuteableConnectionLogger.class::cast)
                .forEach(MuteableConnectionLogger::mute);
    }

    @Override
    public void unmute() {
        connectionLoggers.stream()
                .filter(MuteableConnectionLogger.class::isInstance)
                .map(MuteableConnectionLogger.class::cast)
                .forEach(MuteableConnectionLogger::unmute);
    }

    @Override
    public boolean isMuted() {
        return connectionLoggers.stream()
                .filter(MuteableConnectionLogger.class::isInstance)
                .map(MuteableConnectionLogger.class::cast)
                .allMatch(MuteableConnectionLogger::isMuted);
    }

    @Override
    public void close() throws IOException {
        for (final var connectionLogger : connectionLoggers) {
            connectionLogger.close();
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CompoundConnectionLogger that = (CompoundConnectionLogger) o;
        return Objects.equals(connectionLoggers, that.connectionLoggers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionLoggers);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "connectionLoggers=" + connectionLoggers +
                "]";
    }
}
