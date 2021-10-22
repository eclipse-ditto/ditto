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
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.connectivity.model.MessageSendingFailedException;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.internal.models.signal.correlation.CommandAndCommandResponseMatchingValidator;

/**
 * Validates that a specified {@link org.eclipse.ditto.base.model.signals.SignalWithEntityId} and
 * {@link CommandResponse} are associated with each other, i.e. that the command response correlates to a command.
 * <p>
 * Both signals correlate if
 * <ul>
 *     <li>their correlation IDs match,</li>
 *     <li>their signal types match and</li>
 *     <li>their entity IDs match.</li>
 * </ul>
 * </p>
 * <p>
 * If any of the above evaluates to {@code false} a {@link MessageSendingFailedException} is thrown with a detail
 * message describing the cause.
 * Furthermore the exception gets logged for the command response via
 * {@link ConnectionLogger#failure(org.eclipse.ditto.base.model.signals.Signal, org.eclipse.ditto.base.model.exceptions.DittoRuntimeException)}.
 * </p>
 */
@NotThreadSafe
final class HttpPushRoundTripSignalsValidator implements BiConsumer<SignalWithEntityId<?>, CommandResponse<?>> {

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
    public void accept(final SignalWithEntityId<?> signalWithEntityId, final CommandResponse<?> commandResponse) {
        final var validationResult = validator.apply(signalWithEntityId, commandResponse);
        if (!validationResult.isSuccess()) {
            final var messageSendingFailedException = MessageSendingFailedException.newBuilder()
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .message(validationResult.getDetailMessageOrThrow())
                    .dittoHeaders(signalWithEntityId.getDittoHeaders())
                    .build();
            connectionLogger.failure(commandResponse, messageSendingFailedException);
            throw messageSendingFailedException;
        }
    }

}
