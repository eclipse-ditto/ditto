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

package org.eclipse.ditto.connectivity.service.messaging.monitoring.logs;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;

/**
 * Default implementation of {@link MuteableConnectionLogger}.
 * This implementation is not threadsafe since it ain't really of a big importance if a log message gets lost
 * during activation of the logger.
 * <p>
 * In case of an exception this implementation switches it's delegate to an {@link ExceptionalConnectionLogger}.
 */
@NotThreadSafe
final class DefaultMuteableConnectionLogger implements MuteableConnectionLogger {

    private final ThreadSafeDittoLogger logger;
    private final ConnectionId connectionId;
    private ConnectionLogger delegate;
    private boolean active;

    /**
     * Create a new mutable connection logger that is currently muted.
     *
     * @param connectionId the connection for which the logger is logging.
     * @param delegate the delegate to call while the logger is unmuted
     */
    DefaultMuteableConnectionLogger(final ConnectionId connectionId, final ConnectionLogger delegate) {
        this.connectionId = connectionId;
        final var threadSafeLogger = DittoLoggerFactory.getThreadSafeLogger(DefaultMuteableConnectionLogger.class);
        logger = threadSafeLogger.withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID.toString(), connectionId);
        this.delegate = delegate;
        active = false;
    }

    @Override
    public void mute() {
        logTrace("Muting the logger");
        active = false;
    }

    private void logTrace(final String message) {
        if (logger.isTraceEnabled()) {
            logger.trace(message);
        }
    }

    @Override
    public void unmute() {
        logTrace("Unmuting the logger");
        active = true;
    }

    @Override
    public boolean isMuted() {
        return !active;
    }

    @Override
    public void success(final ConnectionMonitor.InfoProvider infoProvider) {
        if (isMuted()) {
            logTraceWithCorrelationId(infoProvider.getCorrelationId(), "Not logging success since logger is muted.");
        } else {
            wrapInExceptionHandling(() -> delegate.success(infoProvider));
        }
    }

    @Override
    public void success(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final Object... messageArguments) {

        if (isMuted()) {
            logTraceWithCorrelationId(infoProvider.getCorrelationId(), "Not logging success since logger is muted.");
        } else {
            wrapInExceptionHandling(() -> delegate.success(infoProvider, message, messageArguments));
        }
    }

    @Override
    public void failure(final ConnectionMonitor.InfoProvider infoProvider,
            @Nullable final DittoRuntimeException exception) {

        if (isMuted()) {
            logTraceWithCorrelationId(infoProvider.getCorrelationId(), "Not logging failure since logger is muted.");
        } else {
            wrapInExceptionHandling(() -> delegate.failure(infoProvider, exception));
        }
    }

    @Override
    public void failure(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final Object... messageArguments) {

        if (isMuted()) {
            logTraceWithCorrelationId(infoProvider.getCorrelationId(), "Not logging failure since logger is muted.");
        } else {
            wrapInExceptionHandling(() -> delegate.failure(infoProvider, message, messageArguments));
        }
    }

    @Override
    public void exception(final ConnectionMonitor.InfoProvider infoProvider, @Nullable final Throwable exception) {
        if (isMuted()) {
            logTraceWithCorrelationId(infoProvider.getCorrelationId(), "Not logging exception since logger is muted.");
        } else {
            wrapInExceptionHandling(() -> delegate.exception(infoProvider, exception));
        }
    }

    @Override
    public void exception(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final Object... messageArguments) {

        if (isMuted()) {
            logTraceWithCorrelationId(infoProvider.getCorrelationId(), "Not logging exception since logger is muted.");
        } else {
            wrapInExceptionHandling(() -> delegate.exception(infoProvider, message, messageArguments));
        }
    }

    @Override
    public void clear() {
        wrapInExceptionHandling(() -> delegate.clear());
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public void logEntry(final LogEntry logEntry) {
        ConditionChecker.checkNotNull(logEntry, "logEntry");
        if (isMuted()) {
            logTraceWithCorrelationId(logEntry.getCorrelationId(), "Not logging entry since logger is muted.");
        } else {
            wrapInExceptionHandling(() -> delegate.logEntry(logEntry));
        }
    }

    @Override
    public Collection<LogEntry> getLogs() {
        final Collection<LogEntry> result;
        if (isMuted()) {
            logger.trace("Returning empty logs since logger is muted.");
            result = Collections.emptyList();
        } else {
            result = delegate.getLogs();
        }
        return result;
    }

    private void wrapInExceptionHandling(final Runnable runnable) {
        try {
            runnable.run();
        } catch (final Exception e) {
            logger.error("Encountered exception: <{}> in connection logger: <{}>. Switching delegate to: <{}>.",
                    e,
                    this,
                    ExceptionalConnectionLogger.class.getSimpleName());
            delegate = ConnectionLoggerFactory.newExceptionalLogger(connectionId, e);
        }
    }

    private void logTraceWithCorrelationId(final CharSequence correlationId,
            final String message,
            final Object... messageArguments) {

        if (logger.isTraceEnabled()) {
            logger.withCorrelationId(correlationId).trace(message, messageArguments);
        }
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (DefaultMuteableConnectionLogger) o;
        return active == that.active &&
                Objects.equals(connectionId, that.connectionId) &&
                Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionId, delegate, active);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "connectionId=" + connectionId +
                ", delegate=" + delegate +
                ", active=" + active +
                "]";
    }

}
