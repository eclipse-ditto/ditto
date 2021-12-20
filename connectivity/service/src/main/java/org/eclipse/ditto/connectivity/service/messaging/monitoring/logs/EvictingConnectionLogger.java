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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.LogCategory;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.model.LogLevel;
import org.eclipse.ditto.connectivity.model.LogType;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;

/**
 * Implementation of {@link ConnectionLogger} that
 * has fixed capacity for its success and failure logs and will evict old logs when new logs are added.
 */
final class EvictingConnectionLogger extends AbstractConnectionLogger<EvictingConnectionLogger.Builder, EvictingConnectionLogger> {

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(EvictingConnectionLogger.class);

    private final EvictingQueue<LogEntry> successLogs;
    private final EvictingQueue<LogEntry> failureLogs;

    private EvictingConnectionLogger(final Builder builder) {
        super(builder);

        successLogs = DefaultEvictingQueue.withCapacity(builder.successCapacity);
        failureLogs = DefaultEvictingQueue.withCapacity(builder.failureCapacity);

        LOGGER.trace("Successfully built new EvictingConnectionLogger: {}", this);
    }

    /**
     * Create a new builder.
     *
     * @param successCapacity how many success logs should be stored by the logger.
     * @param failureCapacity how many failure logs should be stored by the logger.
     * @param category category of logs stored by the logger.
     * @param type type of logs stored by the logger.
     * @return a new Builder for {@code EvictingConnectionLogger}.
     * @throws java.lang.NullPointerException if any non-nullable argument is {@code null}.
     */
    static Builder newBuilder(final int successCapacity,
            final int failureCapacity,
            final LogCategory category,
            final LogType type) {

        return new Builder(successCapacity, failureCapacity, category, type);
    }

    @Override
    public void success(final ConnectionMonitor.InfoProvider infoProvider,
            final String message,
            final Object... messageArguments) {

        final var logTimer = startConnectionLogTimer();
        final var formattedMessage = formatMessage(infoProvider, message, messageArguments);
        logTimer.startNewSegment("message_prepared");
        final var logEntry = getLogEntry(infoProvider, formattedMessage, LogLevel.SUCCESS);
        logTraceWithCorrelationId(logEntry);
        logTimer.startNewSegment("message_internally_logged");
        successLogs.add(logEntry);
        logTimer.stop();
    }

    private static StartedTimer startConnectionLogTimer() {
        final var timer = DittoMetrics.timer("connection_log");
        return timer.start();
    }

    private static void logTraceWithCorrelationId(final LogEntry logEntry) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.withCorrelationId(logEntry.getCorrelationId())
                    .trace("Saving {} log at <{}> for entity <{}> with message: {}",
                            logEntry.getLogLevel(),
                            logEntry.getTimestamp(),
                            logEntry.getEntityId().orElse(null),
                            logEntry.getMessage());
        }
    }

    @Override
    public void failure(final ConnectionMonitor.InfoProvider infoProvider,
            final String message,
            final Object... messageArguments) {

        logFailureEntry(getLogEntry(infoProvider,
                formatMessage(infoProvider, message, messageArguments),
                LogLevel.FAILURE));
    }

    private void logFailureEntry(final LogEntry logEntry) {
        logTraceWithCorrelationId(logEntry);
        failureLogs.add(logEntry);
    }

    @Override
    public void exception(final ConnectionMonitor.InfoProvider infoProvider,
            final String message,
            final Object... messageArguments) {

        final var formattedMessage = formatMessage(infoProvider, message, messageArguments);
        logTraceExceptionWithCorrelationId(infoProvider.getCorrelationId(), infoProvider, formattedMessage);
        failureLogs.add(getLogEntry(infoProvider, formattedMessage, LogLevel.FAILURE));
    }

    private static void logTraceExceptionWithCorrelationId(final CharSequence correlationId,
            final ConnectionMonitor.InfoProvider infoProvider,
            final String message) {

        if (LOGGER.isTraceEnabled()) {
            LOGGER.withCorrelationId(correlationId)
                    .trace("Saving exception log at <{}> for entity <{}> with message: {}",
                            infoProvider.getTimestamp(),
                            infoProvider.getEntityId(),
                            message);
        }
    }

    @Override
    public void clear() {
        LOGGER.trace("Clearing all logs.");
        successLogs.clear();
        failureLogs.clear();
    }

    @Override
    public void close() throws IOException {
        clear();
    }

    @Override
    public void logEntry(final LogEntry logEntry) {
        checkNotNull(logEntry, "logEntry");
        if (LogLevel.SUCCESS == logEntry.getLogLevel()) {
            final var logTimer = startConnectionLogTimer();
            logTraceWithCorrelationId(logEntry);
            logTimer.startNewSegment("message_internally_logged");
            successLogs.add(logEntry);
            logTimer.stop();
        } else {
            logFailureEntry(logEntry);
        }
    }

    @Override
    public Collection<LogEntry> getLogs() {
        final Collection<LogEntry> logs = new ArrayList<>(successLogs.size() + failureLogs.size());
        logs.addAll(successLogs);
        logs.addAll(failureLogs);

        LOGGER.trace("Returning logs: {}", logs);
        return logs;
    }

    @SuppressWarnings("OverlyComplexMethod")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final EvictingConnectionLogger that = (EvictingConnectionLogger) o;
        return Objects.equals(successLogs, that.successLogs) &&
                Objects.equals(failureLogs, that.failureLogs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), successLogs, failureLogs);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", successLogs=" + successLogs +
                ", failureLogs=" + failureLogs +
                "]";
    }

    /**
     * Builder for {@code EvictingConnectionLogger}.
     */
    static final class Builder extends AbstractConnectionLoggerBuilder<Builder, EvictingConnectionLogger> {

        private final int successCapacity;
        private final int failureCapacity;

        private Builder(final int successCapacity,
                final int failureCapacity,
                final LogCategory category,
                final LogType type) {
            super(category, type);
            this.successCapacity = successCapacity;
            this.failureCapacity = failureCapacity;
        }

        @Override
        public EvictingConnectionLogger build() {
            return new EvictingConnectionLogger(this);
        }

    }

}
