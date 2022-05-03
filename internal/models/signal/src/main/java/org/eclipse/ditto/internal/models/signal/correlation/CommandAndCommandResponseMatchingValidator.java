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
package org.eclipse.ditto.internal.models.signal.correlation;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.UnsupportedSignalException;
import org.eclipse.ditto.base.model.signals.WithType;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.internal.models.signal.type.SemanticSignalType;
import org.eclipse.ditto.internal.models.signal.type.SignalTypeFormatException;

/**
 * Validates that a specified {@link Command} and {@link CommandResponse} are associated with each other, i.e. that the
 * command response correlates to a command.
 * <p>
 * Both signals correlate if
 * <ul>
 *     <li>their correlation IDs match,</li>
 *     <li>their signal types match,</li>
 *     <li>their entity IDs match and</li>
 *     <li>their resource paths match &ndash; in case of a {@code ThingCommand}.</li>
 * </ul>
 * <p>
 * If any of the above evaluates to {@code false} the yielded {@link MatchingValidationResult} is a failure, else it is
 * a success.
 * </p>
 * <p>
 * If the type of the command or command response is invalid an {@link UnsupportedSignalException} is thrown.
 * </p>
 * <p>
 * @since 2.3.0
 */
@Immutable
public final class CommandAndCommandResponseMatchingValidator
        implements BiFunction<Command<?>, CommandResponse<?>, MatchingValidationResult> {

    private CommandAndCommandResponseMatchingValidator() {
        super();
    }

    /**
     * Returns an instance of {@code CommandAndCommandResponseMatchingValidator}.
     *
     * @return the instance.
     */
    public static CommandAndCommandResponseMatchingValidator getInstance() {
        return new CommandAndCommandResponseMatchingValidator();
    }

    @Override
    public MatchingValidationResult apply(final Command<?> sentCommand, final CommandResponse<?> commandResponse) {
        var result = validateCorrelationIdsMatch(sentCommand, commandResponse);
        if (result.isSuccess()) {
            result = validateTypesMatch(sentCommand, commandResponse);
        }
        if (result.isSuccess()) {
            result = validateEntityIdsMatch(sentCommand, commandResponse);
        }
        if (result.isSuccess()) {
            result = validateResourcePathsMatch(sentCommand, commandResponse);
        }

        return result;
    }

    private static MatchingValidationResult validateCorrelationIdsMatch(final Command<?> command,
            final CommandResponse<?> commandResponse) {

        final MatchingValidationResult result;
        if (doCommandAndResponseCorrelationIdsMatch(command, commandResponse)) {
            result = MatchingValidationResult.success();
        } else {
            final var pattern = "Correlation ID of live response <{0}> differs from" +
                    " correlation ID of command <{1}>.";
            result = MatchingValidationResult.failure(command,
                    commandResponse,
                    MessageFormat.format(pattern,
                            getCorrelationId(commandResponse).orElse(null),
                            getCorrelationId(command).orElse(null)));
        }

        return result;
    }

    /**
     * Checks whether the {@code command} and {@code commandResponse} correlation-ids are either completely equal or
     * if the response's correlation-id "starts with" the command's correlation-id.
     * That could be the case for correlation-id collisions detected in {@code ResponseReceiverCache} in
     * which case a newly created UUID is appended to the collided previous correlation-id.
     *
     * @param command the command to extract the correlation-id to check.
     * @param commandResponse the commandResponse to extract the correlation-id to check.
     * @return {@code true} if the IDs match.
     */
    private static boolean doCommandAndResponseCorrelationIdsMatch(final Command<?> command,
            final CommandResponse<?> commandResponse) {

        final var commandCorrelationId = getCorrelationId(command);
        final var commandResponseCorrelationId = getCorrelationId(commandResponse);
        return commandCorrelationId.equals(commandResponseCorrelationId) ||
                commandResponseCorrelationId.filter(responseId ->
                        commandCorrelationId.filter(responseId::startsWith).isPresent()
                ).isPresent();
    }

    private static Optional<String> getCorrelationId(final Signal<?> signal) {
        return WithDittoHeaders.getCorrelationId(signal);
    }

    private static MatchingValidationResult validateTypesMatch(final Command<?> command,
            final CommandResponse<?> commandResponse) {

        final MatchingValidationResult result;

        if (isAcknowledgement(commandResponse) || isErrorResponseType(commandResponse)) {
            result = MatchingValidationResult.success();
        } else {
            final var semanticCommandResponseType = tryToParseSemanticSignalType(commandResponse);
            final var semanticCommandType = tryToParseSemanticSignalType(command);

            if (!isSameSignalDomain(semanticCommandType, semanticCommandResponseType)) {
                result = MatchingValidationResult.failure(command,
                        commandResponse,
                        getMessageForMismatchingTypes(commandResponse, command));
            } else if (CommandResponse.isMessageCommandResponse(commandResponse)) {
                if (!areCorrespondingMessageSignals(semanticCommandType, semanticCommandResponseType)) {
                    result = MatchingValidationResult.failure(command,
                            commandResponse,
                            getMessageForMismatchingTypes(commandResponse, command));
                } else {
                    result = MatchingValidationResult.success();
                }
            } else if (!isEqualNames(semanticCommandType, semanticCommandResponseType)) {
                result = MatchingValidationResult.failure(command,
                        commandResponse,
                        getMessageForMismatchingTypes(commandResponse, command));
            } else {
                result = MatchingValidationResult.success();
            }
        }

        return result;
    }

    private static SemanticSignalType tryToParseSemanticSignalType(final Signal<?> signal) {
        try {
            return SemanticSignalType.parseSemanticSignalType(signal.getType());
        } catch (final SignalTypeFormatException e) {

            // This should never ever happen in production as implementations
            // of Signal are supposed to have a valid type.
            throw UnsupportedSignalException.newBuilder(signal.getType())
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .dittoHeaders(signal.getDittoHeaders())
                    .description("The signal has an invalid type: " + e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    private static String getMessageForMismatchingTypes(final WithType commandResponse, final WithType command) {
        return MessageFormat.format("Type of live response <{0}> is not related to type of command <{1}>.",
                commandResponse.getType(),
                command.getType());
    }

    private static boolean isAcknowledgement(final WithType commandResponse) {
        return Acknowledgement.TYPE.equals(commandResponse.getType());
    }

    private static boolean isErrorResponseType(final CommandResponse<?> commandResponse) {
        return ResponseType.ERROR == commandResponse.getResponseType();
    }

    private static boolean isSameSignalDomain(final SemanticSignalType semanticCommandType,
            final SemanticSignalType semanticCommandResponseType) {

        return Objects.equals(semanticCommandResponseType.getSignalDomain(), semanticCommandType.getSignalDomain());
    }

    private static boolean areCorrespondingMessageSignals(final SemanticSignalType commandType,
            final SemanticSignalType commandResponseType) {

        final var commandName = commandType.getSignalName();
        final var commandResponseName = commandResponseType.getSignalName();
        final var indexOfResponseMessageSuffix = commandResponseName.indexOf("ResponseMessage");

        return commandName.startsWith(commandResponseName.substring(0, indexOfResponseMessageSuffix));
    }

    private static boolean isEqualNames(final SemanticSignalType command, final SemanticSignalType commandResponse) {
        return Objects.equals(command.getSignalName(), commandResponse.getSignalName());
    }

    private static MatchingValidationResult validateEntityIdsMatch(final Command<?> command,
            final CommandResponse<?> commandResponse) {

        final MatchingValidationResult result;

        final var commandEntityId = getEntityId(command);
        final var commandResponseEntityId = getEntityId(commandResponse);
        if (commandEntityId.equals(commandResponseEntityId)) {
            result = MatchingValidationResult.success();
        } else {
            result = MatchingValidationResult.failure(command,
                    commandResponse,
                    MessageFormat.format("Entity ID of live response <{0}> differs from entity ID of command <{1}>.",
                            commandResponseEntityId.orElse(null),
                            commandEntityId.orElse(null)));
        }

        return result;
    }

    private static Optional<EntityId> getEntityId(final Signal<?> signal) {
        return WithEntityId.getEntityId(signal);
    }

    private static MatchingValidationResult validateResourcePathsMatch(final Command<?> command,
            final CommandResponse<?> commandResponse) {

        final MatchingValidationResult result;
        if (Command.isThingCommand(command)) {
            final var commandResourcePath = command.getResourcePath();
            final var commandResponseResourcePath = commandResponse.getResourcePath();
            if (commandResourcePath.equals(commandResponseResourcePath) ||
                    isAcknowledgement(commandResponse) ||
                    isErrorResponseType(commandResponse)) {

                result = MatchingValidationResult.success();
            } else {
                final var pattern = "Resource path of live response <{0}> differs from resource path of command <{1}>.";
                result = MatchingValidationResult.failure(command,
                        commandResponse,
                        MessageFormat.format(pattern, commandResponseResourcePath, commandResourcePath));
            }
        } else {
            result = MatchingValidationResult.success();
        }
        return result;
    }

}
