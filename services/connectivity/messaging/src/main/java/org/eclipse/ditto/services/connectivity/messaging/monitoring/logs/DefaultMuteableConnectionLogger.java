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
import java.util.Collections;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.LogEntry;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.MuteableConnectionLogger}.
 * This implementation is not threadsafe since it ain't really of a big importance if a log message gets lost during activation of the logger.
 */
@NotThreadSafe
final class DefaultMuteableConnectionLogger implements MuteableConnectionLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMuteableConnectionLogger.class);

    private final String connectionId;
    private final ConnectionLogger delegate;
    private boolean active;

    /**
     * Create a new mutable connection logger that is currently muted.
     * @param connectionId the connection for which the logger is logging.
     * @param delegate the delegate to call while the logger is unmuted
     */
    DefaultMuteableConnectionLogger(final String connectionId, final ConnectionLogger delegate) {
        this.connectionId = connectionId;
        this.delegate = delegate;
        this.active = false;
    }

    @Override
    public void mute() {
        logTrace("Muting the logger");
        active = false;
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
        if (active) {
            delegate.success(infoProvider);
        } else {
            logTraceWithCorrelationId("Not logging success since logger is muted.", infoProvider);
        }
    }

    @Override
    public void success(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final Object... messageArguments) {
        if (active) {
            delegate.success(infoProvider, message, messageArguments);
        } else {
            logTraceWithCorrelationId("Not logging success since logger is muted.", infoProvider);
        }
    }

    @Override
    public void failure(final ConnectionMonitor.InfoProvider infoProvider, @Nullable final DittoRuntimeException exception) {
        if (active) {
            delegate.failure(infoProvider, exception);
        } else {
            logTraceWithCorrelationId("Not logging failure since logger is muted.", infoProvider);
        }
    }

    @Override
    public void failure(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final Object... messageArguments) {
        if (active) {
            delegate.failure(infoProvider, message, messageArguments);
        } else {
            logTraceWithCorrelationId("Not logging failure since logger is muted.", infoProvider);
        }
    }

    @Override
    public void exception(final ConnectionMonitor.InfoProvider infoProvider, @Nullable final Exception exception) {
        if (active) {
            delegate.exception(infoProvider, exception);
        } else {
            logTraceWithCorrelationId("Not logging exception since logger is muted.", infoProvider);
        }
    }

    @Override
    public void exception(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final Object... messageArguments) {
        if (active) {
            delegate.exception(infoProvider, message, messageArguments);
        } else {
            logTraceWithCorrelationId("Not logging exception since logger is muted.", infoProvider);
        }
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public Collection<LogEntry> getLogs() {
        if (active) {
            return delegate.getLogs();
        }
        LOGGER.trace("Returning empty logs since logger is muted.");
        return Collections.emptyList();
    }

    private void logTrace(final String message) {
        if (LOGGER.isTraceEnabled()) {
            ConnectionLogUtil.enhanceLogWithConnectionId(connectionId);
            LOGGER.trace(message);
        }
    }

    private void logTraceWithCorrelationId(final String message, final ConnectionMonitor.InfoProvider infoProvider, final Object... messageArguments) {
        if (LOGGER.isTraceEnabled()) {
            ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(infoProvider.getCorrelationId(), connectionId);
            LOGGER.trace(message, messageArguments);
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
        final DefaultMuteableConnectionLogger that = (DefaultMuteableConnectionLogger) o;
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
                ", connectionId=" + connectionId +
                ", delegate=" + delegate +
                ", active=" + active +
                "]";
    }

}
