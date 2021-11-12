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
package org.eclipse.ditto.connectivity.service.messaging.httppush;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.function.BiConsumer;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.signals.UnsupportedSignalException;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.connectivity.api.messaging.monitoring.logs.LogEntryFactory;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.internal.models.signal.correlation.CommandAndCommandResponseMatchingValidator;

/**
 * Validates that a specified {@link Command} and {@link CommandResponse} are associated with each other, i.e. that the
 * command response correlates to a command.
 * <p>
 * Both signals correlate if
 * <ul>
 *     <li>their correlation IDs match,</li>
 *     <li>their signal types match and</li>
 *     <li>their entity IDs match.</li>
 * </ul>
 * </p>
 * <p>
 * If any of the above evaluates to {@code false} a {@link UnsupportedSignalException} is thrown with a detail
 * message describing the cause.
 * Furthermore the exception gets logged for the command response via {@link ConnectionLogger#logEntry(LogEntry)}.
 * </p>
 */
@NotThreadSafe
final class HttpPushRoundTripSignalsValidator implements BiConsumer<Command<?>, CommandResponse<?>> {

    private final ConnectionLogger connectionLogger;
    private final CommandAndCommandResponseMatchingValidator validator;

    private HttpPushRoundTripSignalsValidator(final ConnectionLogger connectionLogger) {
        this.connectionLogger = connectionLogger;
        validator = CommandAndCommandResponseMatchingValidator.getInstance();
    }

    static HttpPushRoundTripSignalsValidator newInstance(final ConnectionLogger connectionLogger) {
        return new HttpPushRoundTripSignalsValidator(checkNotNull(connectionLogger, "connectionLogger"));
    }

    @Override
    public void accept(final Command<?> command, final CommandResponse<?> commandResponse) {
        final var validationResult = validator.apply(command, commandResponse);
        if (!validationResult.isSuccess()) {
            final var detailMessage = validationResult.getDetailMessageOrThrow();
            connectionLogger.logEntry(LogEntryFactory.getLogEntryForFailedCommandResponseRoundTrip(command,
                    commandResponse,
                    detailMessage));
            throw newUnsupportedSignalException(command, commandResponse, detailMessage);
        }
    }

    private static UnsupportedSignalException newUnsupportedSignalException(final Command<?> command,
            final CommandResponse<?> commandResponse,
            final String detailMessage) {

        return UnsupportedSignalException.newBuilder(commandResponse.getType())
                .httpStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .message(detailMessage)
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

}
