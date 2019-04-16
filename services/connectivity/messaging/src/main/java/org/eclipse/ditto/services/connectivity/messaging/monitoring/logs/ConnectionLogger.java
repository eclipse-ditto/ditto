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

import java.text.MessageFormat;
import java.time.Instant;
import java.util.Collection;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.LogEntry;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.signals.base.Signal;

// TODO: docs
public interface ConnectionLogger {

    Collection<LogEntry> getLogs();

    void success(final ConnectionMonitor.InfoProvider infoProvider);

    void success(final String correlationId, final Instant timestamp, final String message,
            @Nullable final String thingId);

    void failure(final ConnectionMonitor.InfoProvider infoProvider, @Nullable final DittoRuntimeException exception);

    void failure(final String correlationId, final Instant timestamp, final String message,
            @Nullable final String thingId);

    void exception(final ConnectionMonitor.InfoProvider infoProvider, @Nullable final Exception exception);

    void exception(final String correlationId, final Instant timestamp, final String message, @Nullable final String thingId);

    default void success(final ConnectionMonitor.InfoProvider infoProvider, final String message, final Object... messageArguments) {
        final String formattedMessage = formatMessage(message, messageArguments);
        success(infoProvider.getCorrelationId(), infoProvider.getTimestamp(), formattedMessage,
                infoProvider.getThingId());
    }

    default void failure(final ConnectionMonitor.InfoProvider infoProvider) {
        failure(infoProvider, null);
    }

    default void failure(final Signal<?> signal, @Nullable final DittoRuntimeException exception) {
        failure(ImmutableInfoProvider.forSignal(signal), exception);
    }

    default void failure(final ConnectionMonitor.InfoProvider infoProvider, final String message, final Object... messageArguments) {
        final String formattedMessage = formatMessage(message, messageArguments);
        failure(infoProvider.getCorrelationId(), infoProvider.getTimestamp(), formattedMessage, infoProvider.getThingId());
    }

    default void exception(final ConnectionMonitor.InfoProvider infoProvider) {
        exception(infoProvider, null);
    }

    default void exception(final ConnectionMonitor.InfoProvider infoProvider, final String message, final Object... messageArguments) {
        final String formattedMessage = formatMessage(message, messageArguments);
        exception(infoProvider.getCorrelationId(), infoProvider.getTimestamp(), formattedMessage, infoProvider.getThingId());
    }

    static String formatMessage(final String message, final Object... messageArguments) {
        if (messageArguments.length > 0) {
            return MessageFormat.format(message, messageArguments);
        }
        return message;
    }

}
