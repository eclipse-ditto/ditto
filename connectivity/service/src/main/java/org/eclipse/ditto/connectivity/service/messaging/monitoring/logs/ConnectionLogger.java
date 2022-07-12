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

import java.io.Closeable;
import java.util.Collection;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.service.config.MonitoringLoggerConfig;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;

/**
 * Logger for connections that provides log messages for end users.
 */
public interface ConnectionLogger extends Closeable {

    /**
     * Gets the connection logger for the given connection ID.
     *
     * @param connectionId the ID of the connection.
     * @param config the logger config.
     * @return the logger.
     */
    static ConnectionLogger getInstance(final ConnectionId connectionId, final MonitoringLoggerConfig config) {
        final ConnectionLoggerRegistry connectionLoggerRegistry = ConnectionLoggerRegistry.fromConfig(config);
        return connectionLoggerRegistry.forConnection(connectionId);
    }

    /**
     * Get all log entries stored in this logger.
     *
     * @return the log entries.
     */
    Collection<LogEntry> getLogs();

    /**
     * Log a success event.
     *
     * @param infoProvider containing additional information on the event.
     */
    void success(ConnectionMonitor.InfoProvider infoProvider);

    /**
     * Log a success event.
     *
     * @param infoProvider containing additional information on the event.
     * @param message a custom message that is used for logging the event.
     * @param messageArguments additional message arguments that are part of {@code message}.
     * {@link java.text.MessageFormat#format(String, Object...)} is used for applying message arguments to {@code message}.
     */
    void success(ConnectionMonitor.InfoProvider infoProvider, String message, Object... messageArguments);

    /**
     * Log a failure event.
     *
     * @param infoProvider containing additional information on the event.
     * @param exception the exception that caused a failure. Its message is used in the log entry.
     */
    void failure(ConnectionMonitor.InfoProvider infoProvider, @Nullable DittoRuntimeException exception);

    /**
     * Log a failure event.
     *
     * @param infoProvider containing additional information on the event.
     * @param message a custom message that is used for logging the event.
     * @param messageArguments additional message arguments that are part of {@code message}.
     * {@link java.text.MessageFormat#format(String, Object...)} is used for applying message arguments to {@code message}.
     */
    void failure(ConnectionMonitor.InfoProvider infoProvider, String message, Object... messageArguments);

    /**
     * Log a failure event.
     *
     * @param infoProvider containing additional information on the event.
     * @param exception the exception that caused a failure. Its message is used in the log entry.
     */
    void exception(ConnectionMonitor.InfoProvider infoProvider, @Nullable Throwable exception);

    /**
     * Log an exception event.
     *
     * @param infoProvider containing additional information on the event.
     * @param message a custom message that is used for logging the event.
     * @param messageArguments additional message arguments that are part of {@code message}.
     * {@link java.text.MessageFormat#format(String, Object...)} is used for applying message arguments to {@code message}.
     */
    void exception(ConnectionMonitor.InfoProvider infoProvider, String message, Object... messageArguments);

    /**
     * Clears the logs.
     */
    void clear();

    /**
     * Log a success event.
     *
     * @param message a custom message that is used for logging the event.
     * @param messageArguments additional message arguments that are part of {@code message}.
     */
    default void success(final String message, final Object... messageArguments) {
        success(InfoProviderFactory.empty(), message, messageArguments);
    }

    /**
     * Log a failure event.
     *
     * @param message a custom message that is used for logging the event.
     * @param messageArguments additional message arguments that are part of {@code message}.
     */
    default void failure(final String message, final Object... messageArguments) {
        failure(InfoProviderFactory.empty(), message, messageArguments);
    }

    /**
     * Log a failure event.
     *
     * @param infoProvider containing additional information on the event.
     */
    default void failure(final ConnectionMonitor.InfoProvider infoProvider) {
        failure(infoProvider, null);
    }

    /**
     * Log a failure event.
     *
     * @param signal containing additional information on the event.
     * @param exception the exception that caused a failure. Its message is used in the log entry.
     */
    default void failure(final Signal<?> signal, @Nullable final DittoRuntimeException exception) {
        failure(InfoProviderFactory.forSignal(signal), exception);
    }

    /**
     * Log an exception event.
     *
     * @param message a custom message that is used for logging the event.
     * @param messageArguments additional message arguments that are part of {@code message}.
     */
    default void exception(final String message, final Object... messageArguments) {
        exception(InfoProviderFactory.empty(), message, messageArguments);
    }

    /**
     * Log an exception event.
     *
     * @param infoProvider containing additional information on the event.
     */
    default void exception(final ConnectionMonitor.InfoProvider infoProvider) {
        exception(infoProvider, null);
    }

    /**
     * Logs the specified {@code LogEntry} argument.
     *
     * @param logEntry the entry to be logged.
     * @throws NullPointerException if {@code logEntry} is {@code null}.
     * @since 2.3.0
     */
    void logEntry(LogEntry logEntry);

}
