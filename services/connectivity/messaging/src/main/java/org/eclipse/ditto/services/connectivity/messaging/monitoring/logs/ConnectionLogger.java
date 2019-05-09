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

package org.eclipse.ditto.services.connectivity.messaging.monitoring.logs;

import java.util.Collection;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.LogEntry;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.signals.base.Signal;

/**
 * Logger for connections that provides log messages for end users.
 */
public interface ConnectionLogger {

    /**
     * Get all log entries stored in this logger.
     * @return the log entries.
     */
    Collection<LogEntry> getLogs();

    /**
     * Log a success event.
     * @param infoProvider containing additional information on the event.
     */
    void success(final ConnectionMonitor.InfoProvider infoProvider);

    /**
     * Log a success event.
     * @param infoProvider containing additional information on the event.
     * @param message a custom message that is used for logging the event.
     * @param messageArguments additional message arguments that are part of {@code message}.
     * {@link java.text.MessageFormat#format(String, Object...)} is used for applying message arguments to {@code message}.
     */
    void success(final ConnectionMonitor.InfoProvider infoProvider, final String message, final Object... messageArguments);

    /**
     * Log a failure event.
     * @param infoProvider containing additional information on the event.
     * @param exception the exception that caused a failure. Its message is used in the log entry.
     */
    void failure(final ConnectionMonitor.InfoProvider infoProvider, @Nullable final DittoRuntimeException exception);

    /**
     * Log a failure event.
     * @param infoProvider containing additional information on the event.
     * @param message a custom message that is used for logging the event.
     * @param messageArguments additional message arguments that are part of {@code message}.
     * {@link java.text.MessageFormat#format(String, Object...)} is used for applying message arguments to {@code message}.
     */
    void failure(final ConnectionMonitor.InfoProvider infoProvider, final String message, final Object... messageArguments);

    /**
     * Log a failure event.
     * @param infoProvider containing additional information on the event.
     * @param exception the exception that caused a failure. Its message is used in the log entry.
     */
    void exception(final ConnectionMonitor.InfoProvider infoProvider, @Nullable final Exception exception);

    /**
     * Log an exception event.
     * @param infoProvider containing additional information on the event.
     * @param message a custom message that is used for logging the event.
     * @param messageArguments additional message arguments that are part of {@code message}.
     * {@link java.text.MessageFormat#format(String, Object...)} is used for applying message arguments to {@code message}.
     */
    void exception(final ConnectionMonitor.InfoProvider infoProvider, final String message, final Object... messageArguments);

    /**
     * Clears the logs.
     */
    void clear();

    /**
     * Log a failure event.
     * @param infoProvider containing additional information on the event.
     */
    default void failure(final ConnectionMonitor.InfoProvider infoProvider) {
        failure(infoProvider, null);
    }

    /**
     * Log a failure event.
     * @param signal containing additional information on the event.
     * @param exception the exception that caused a failure. Its message is used in the log entry.
     */
    default void failure(final Signal<?> signal, @Nullable final DittoRuntimeException exception) {
        failure(ImmutableInfoProvider.forSignal(signal), exception);
    }

    /**
     * Log an exception event.
     * @param infoProvider containing additional information on the event.
     */
    default void exception(final ConnectionMonitor.InfoProvider infoProvider) {
        exception(infoProvider, null);
    }

}
