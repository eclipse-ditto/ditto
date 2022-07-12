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
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;

/**
 * An exceptional connection logger, which logs method calls on tracing to allow for analyzing missed logs.
 */
public final class ExceptionalConnectionLogger implements ConnectionLogger {

    private final ThreadSafeDittoLogger logger;
    private final ConnectionId connectionId;
    private final Exception exception;

    /**
     * Constructs a {@code ExceptionalConnectionLogger}.
     *
     * @param connectionId the connection for which the logger is logging.
     * @param exception the exception which caused the initialization of this logger.
     */
    ExceptionalConnectionLogger(final ConnectionId connectionId, final Exception exception) {
        this.exception = exception;
        this.connectionId = connectionId;
        final var threadSafeLogger = DittoLoggerFactory.getThreadSafeLogger(ExceptionalConnectionLogger.class);
        logger = threadSafeLogger.withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connectionId);
    }

    @Override
    public void success(final ConnectionMonitor.InfoProvider infoProvider) {
        logTraceWithCorrelationId(infoProvider.getCorrelationId(), "Not logging success since logger is exceptional.");
    }

    private void logTraceWithCorrelationId(final CharSequence correlationId,
            final String traceLogMessage,
            final Object... messageArguments) {

        if (logger.isTraceEnabled()) {
            logger.withCorrelationId(correlationId).trace(traceLogMessage, messageArguments);
        }
    }

    @Override
    public void success(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final Object... messageArguments) {

        logTraceWithCorrelationId(infoProvider.getCorrelationId(),
                "Not logging success with message <{}> and arguments <{}> since logger is exceptional.",
                message,
                messageArguments);
    }

    @Override
    public void failure(final ConnectionMonitor.InfoProvider infoProvider,
            @Nullable final DittoRuntimeException exception) {

        logTraceWithCorrelationId(infoProvider.getCorrelationId(),
                "Not logging failure <{}> since logger is exceptional.",
                exception);
    }

    @Override
    public void failure(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final Object... messageArguments) {

        final var pattern = "Not logging failure <{0}> with arguments <{1}> since logger is exceptional.";
        final var traceLogMessage = MessageFormat.format(pattern, exception, messageArguments);
        logTraceWithCorrelationId(infoProvider.getCorrelationId(), traceLogMessage);
    }

    @Override
    public void exception(final ConnectionMonitor.InfoProvider infoProvider, @Nullable final Throwable exception) {
        logTraceWithCorrelationId(infoProvider.getCorrelationId(),
                "Not logging exception <{}> since logger is exceptional.",
                exception);
    }

    @Override
    public void exception(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final Object... messageArguments) {

        final var pattern = "Not logging exception <{0}> with arguments: <{1}> since logger is exceptional.";
        final var traceLogMessage = MessageFormat.format(pattern, exception, messageArguments);
        logTraceWithCorrelationId(infoProvider.getCorrelationId(), traceLogMessage);
    }

    @Override
    public void clear() {
        logger.trace("Not clearing logs since logger is exceptional.");
    }

    @Override
    public void close() throws IOException {
        clear();
    }

    @Override
    public void logEntry(final LogEntry logEntry) {
        logTraceWithCorrelationId(logEntry.getCorrelationId(),
                "Not logging log entry <{}> since logger is exceptional.",
                logEntry);
    }

    @Override
    public Collection<LogEntry> getLogs() {
        logger.trace("Returning empty logs since logger is exceptional.");
        return Collections.emptyList();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (ExceptionalConnectionLogger) o;
        return Objects.equals(connectionId, that.connectionId) &&
                Objects.equals(exception, that.exception);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionId, exception);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "connectionId=" + connectionId +
                ", exception=" + exception +
                "]";
    }

}
