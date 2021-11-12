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
package org.eclipse.ditto.connectivity.api.messaging.monitoring.logs;

import static org.eclipse.ditto.internal.models.signal.SignalInformationPoint.getCorrelationId;
import static org.eclipse.ditto.internal.models.signal.SignalInformationPoint.getEntityId;

import java.time.Instant;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.LogCategory;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.model.LogLevel;
import org.eclipse.ditto.connectivity.model.LogType;

/**
 * Factory for creating instances of {@link LogEntry}.
 *
 * @since 2.2.0
 */
@Immutable
public final class LogEntryFactory {

    private LogEntryFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a {@code LogEntry} for a failed round-trip of the specified {@code Command} and {@code CommandResponse}.
     * The failure is described by the specified detail message string argument.
     *
     * @param command the command of the round-trip.
     * @param commandResponse the response of the round-trip.
     * @param detailMessage describes the reason for the failed round-trip.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code detailMessage} is blank.
     */
    public static LogEntry getLogEntryForFailedCommandResponseRoundTrip(final Command<?> command,
            final CommandResponse<?> commandResponse,
            final String detailMessage) {

        ConditionChecker.checkNotNull(command, "command");
        ConditionChecker.checkNotNull(commandResponse, "commandResponse");
        ConditionChecker.checkArgument(ConditionChecker.checkNotNull(detailMessage, "detailMessage"),
                Predicate.not(String::isBlank),
                () -> "The detailMessage must not be blank.");

        final var logEntryBuilder = ConnectivityModelFactory.newLogEntryBuilder(
                getCorrelationId(command).or(() -> getCorrelationId(commandResponse)).orElse("n/a"),
                Instant.now(),
                LogCategory.RESPONSE,
                LogType.DROPPED,
                LogLevel.FAILURE,
                detailMessage
        );

        getEntityId(command).or(() -> getEntityId(commandResponse)).ifPresent(logEntryBuilder::entityId);

        return logEntryBuilder.build();
    }

}
