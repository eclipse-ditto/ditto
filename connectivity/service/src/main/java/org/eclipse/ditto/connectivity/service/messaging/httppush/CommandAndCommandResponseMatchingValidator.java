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

import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.connectivity.model.MessageSendingFailedException;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;

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
 * {@link ConnectionLogger#failure(Signal, DittoRuntimeException)}.
 * </p>
 */
@NotThreadSafe
final class CommandAndCommandResponseMatchingValidator
        implements BiConsumer<SignalWithEntityId<?>, CommandResponse<?>> {

    private final ConnectionLogger connectionLogger;

    private CommandAndCommandResponseMatchingValidator(final ConnectionLogger connectionLogger) {
        this.connectionLogger = connectionLogger;
    }

    static CommandAndCommandResponseMatchingValidator newInstance(final ConnectionLogger connectionLogger) {
        return new CommandAndCommandResponseMatchingValidator(checkNotNull(connectionLogger, "connectionLogger"));
    }

    @Override
    public void accept(final SignalWithEntityId<?> sentCommand, final CommandResponse<?> commandResponse) {
        final Optional<MessageSendingFailedException> validationError =
                Stream.of(validateCorrelationIdsMatch(sentCommand, commandResponse),
                                validateTypesMatch(sentCommand, commandResponse),
                                validateEntityIdsMatch(sentCommand, commandResponse))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .findFirst();

        if (validationError.isPresent()) {
            final var sendingFailedException = validationError.get();
            connectionLogger.failure(commandResponse, sendingFailedException);
            throw sendingFailedException;
        }
    }

    private static Optional<MessageSendingFailedException> validateCorrelationIdsMatch(final SignalWithEntityId<?> command,
            final CommandResponse<?> commandResponse) {

        String detailMessage = null;
        final var commandDittoHeaders = command.getDittoHeaders();
        final var commandCorrelationIdOptional = commandDittoHeaders.getCorrelationId();
        final var commandResponseDittoHeaders = commandResponse.getDittoHeaders();
        final var commandResponseCorrelationIdOptional = commandResponseDittoHeaders.getCorrelationId();
        if (commandCorrelationIdOptional.isPresent()) {
            final var commandCorrelationId = commandCorrelationIdOptional.get();
            if (commandResponseCorrelationIdOptional.isPresent()) {
                final var commandResponseCorrelationId = commandResponseCorrelationIdOptional.get();
                if (!commandCorrelationId.equals(commandResponseCorrelationId)) {
                    final var pattern =
                            "Correlation ID of live response <{0}> differs from correlation ID of command <{1}>.";
                    detailMessage =
                            MessageFormat.format(pattern, commandResponseCorrelationId, commandCorrelationId);
                }
            } else {
                final var pattern = "Live response has no correlation ID while command has correlation ID <{0}>.";
                detailMessage = MessageFormat.format(pattern, commandCorrelationId);

            }
        } else if (commandResponseCorrelationIdOptional.isPresent()) {
            final var pattern = "Live response has correlation ID <{0}> while command has none.";
            detailMessage = MessageFormat.format(pattern, commandResponseCorrelationIdOptional.get());

        }

        return Optional.ofNullable(detailMessage).map(toMessageSendingFailedException(commandDittoHeaders));
    }

    private static Function<String, MessageSendingFailedException> toMessageSendingFailedException(
            final DittoHeaders dittoHeaders) {

        return detailMessage -> MessageSendingFailedException.newBuilder()
                .httpStatus(HttpStatus.BAD_REQUEST)
                .message(detailMessage)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    private static Optional<MessageSendingFailedException> validateTypesMatch(final SignalWithEntityId<?> command,
            final CommandResponse<?> commandResponse) {

        if (isAcknowledgement(commandResponse) || ResponseType.ERROR == commandResponse.getResponseType()) {
            return Optional.empty();
        }

        String detailMessage = null;
        final var commandResponseType = commandResponse.getType();
        final var semanticCommandResponseType = SemanticSignalType.parseSemanticSignalType(commandResponseType);
        final var semanticCommandType = SemanticSignalType.parseSemanticSignalType(command.getType());
        if (!isSameSignalDomain(semanticCommandType, semanticCommandResponseType)) {
            detailMessage = messageFormatForLiveResponseNotRelatedToTypeOfCommand(commandResponseType, command);
        }
        else if (isMessagesSignalDomain(semanticCommandResponseType)) {
            if (!areCorrespondingMessageSignals(command.getName(), commandResponse.getName())) {
                final var pattern =
                        "Type of live message response <{0}> is not related to type of message command <{1}>.";
                detailMessage = MessageFormat.format(pattern, commandResponse.getType(), command.getType());
            }
        } else if (!isEqualNames(command, commandResponse)) {
            detailMessage = messageFormatForLiveResponseNotRelatedToTypeOfCommand(commandResponseType, command);
        }

        return Optional.ofNullable(detailMessage).map(toMessageSendingFailedException(command.getDittoHeaders()));
    }

    private static String messageFormatForLiveResponseNotRelatedToTypeOfCommand(final String commandResponseType,
            final SignalWithEntityId<?> command) {
        final var pattern = "Type of live response <{0}> is not related to type of command <{1}>.";

        return MessageFormat.format(pattern, commandResponseType, command.getType());
    }

    private static boolean isAcknowledgement(final CommandResponse<?> commandResponse) {
        return Acknowledgement.TYPE.equals(commandResponse.getType());
    }

    private static boolean isSameSignalDomain(final SemanticSignalType semanticCommandType,
            final SemanticSignalType semanticCommandResponseType) {

        return Objects.equals(semanticCommandResponseType.getSignalDomain(), semanticCommandType.getSignalDomain());
    }

    private static boolean isMessagesSignalDomain(final SemanticSignalType semanticCommandResponseType) {
        return "messages".equals(semanticCommandResponseType.getSignalDomain());
    }

    private static boolean areCorrespondingMessageSignals(final String commandName, final String commandResponseName) {
        final var indexOfResponseMessageSuffix = commandResponseName.indexOf("ResponseMessage");

        return commandName.startsWith(commandResponseName.substring(0, indexOfResponseMessageSuffix));
    }

    private static boolean isEqualNames(final SignalWithEntityId<?> command, final CommandResponse<?> commandResponse) {
        return Objects.equals(command.getName(), commandResponse.getName());
    }

    private static Optional<MessageSendingFailedException> validateEntityIdsMatch(final SignalWithEntityId<?> command,
            final CommandResponse<?> commandResponse) {

        String detailMessage = null;
        if (commandResponse instanceof WithEntityId) {
            final var commandResponseEntityId = ((WithEntityId) commandResponse).getEntityId();
            final var commandEntityId = command.getEntityId();
            if (!Objects.equals(commandResponseEntityId, commandEntityId)) {
                final var pattern = "Entity ID of live response <{0}> differs from entity ID of command <{1}>.";
                detailMessage = MessageFormat.format(pattern, commandResponseEntityId, commandEntityId);

            }
        } else {
            final var pattern = "Live response has no entity ID while command has entity ID <{0}>";
            detailMessage = MessageFormat.format(pattern, command.getEntityId());
        }

        return Optional.ofNullable(detailMessage).map(toMessageSendingFailedException(command.getDittoHeaders()));
    }

}
