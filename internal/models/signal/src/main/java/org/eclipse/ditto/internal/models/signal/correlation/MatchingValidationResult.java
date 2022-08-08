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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkArgument;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;

/**
 * Represents the result of validating whether two particular signals are correlated to each other.
 *
 * @since 2.3.0
 */
@Immutable
@SuppressWarnings("java:S1610")
public abstract class MatchingValidationResult {

    private static MatchingValidationResult success = null;

    private MatchingValidationResult() {
        super();
    }

    /**
     * Returns an instance of a successful {@code MatchingValidationResult}.
     *
     * @return the instance.
     */
    public static MatchingValidationResult success() {
        var result = success;
        if (null == result) {
            result = new Success();
            success = result;
        }
        return result;
    }

    /**
     * Returns an instance of a failed {@code MatchingValidationResult}.
     *
     * @param command the command for which the validation failed.
     * @param commandResponse the command response for which the validation failed.
     * @param detailMessage the detail message of the failure.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.base.model.entity.id.EntityIdInvalidException if the {@code DittoHeaders} of
     * {@code commandResponse} contain an invalid value for {@link DittoHeaderDefinition#CONNECTION_ID}.
     * @throws IllegalArgumentException if {@code detailMessage} is empty or blank.
     */
    public static Failure failure(final Command<?> command,
            final CommandResponse<?> commandResponse,
            final String detailMessage) {

        return new Failure(command, commandResponse, detailMessage);
    }

    /**
     * Indicates whether this {@code MatchingValidationResult} represents a successful validation.
     *
     * @return {@code true} if this {@code MatchingValidationResult} is a success, {@code false} else.
     */
    public abstract boolean isSuccess();

    /**
     * Returns this result as {@code Failure}.
     * Throws an exception if this result is a success.
     * To avoid an exception, {@link #isSuccess()} should be checked first.
     *
     * @return this result as {@code Failure}.
     * @throws IllegalStateException if this result is a success.
     * @see #isSuccess()
     */
    public abstract Failure asFailureOrThrow();

    @Immutable
    private static final class Success extends MatchingValidationResult {

        private Success() {
            super();
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public Failure asFailureOrThrow() {
            throw new IllegalStateException("This result is a success and thus cannot be returned as failure.");
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " []";
        }

    }

    /**
     * Represents a failed validation of a command-response-round-trip.
     * This class provides the context for further dealing with the validation result.
     */
    @Immutable
    public static final class Failure extends MatchingValidationResult {

        private final Command<?> command;
        private final CommandResponse<?> commandResponse;
        @Nullable private final String connectionId;
        private final String detailMessage;

        private Failure(final Command<?> command,
                final CommandResponse<?> commandResponse,
                final String detailMessage) {

            this.command = checkNotNull(command, "command");
            this.commandResponse = checkNotNull(commandResponse, "commandResponse");

            connectionId = getConnectionId(commandResponse).orElse(null);
            this.detailMessage = checkArgument(checkNotNull(detailMessage, "detailMessage"),
                    Predicate.not(String::isBlank),
                    () -> "The detailMessage must not be blank.");
        }

        private static Optional<String> getConnectionId(final CommandResponse<?> commandResponse) {
            final var responseDittoHeaders = commandResponse.getDittoHeaders();
            return Optional.ofNullable(responseDittoHeaders.get(DittoHeaderDefinition.CONNECTION_ID.getKey()));
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public Failure asFailureOrThrow() {
            return this;
        }

        /**
         * Returns the detail message that gives information about the cause of the validation failure.
         *
         * @return the detail message.
         */
        public String getDetailMessage() {
            return detailMessage;
        }

        /**
         * Returns the command for which validation failed.
         *
         * @return the command.
         */
        public Command<?> getCommand() {
            return command;
        }

        /**
         * Returns the commandResponse for which validation failed.
         *
         * @return the commandResponse.
         */
        public CommandResponse<?> getCommandResponse() {
            return commandResponse;
        }

        /**
         * Returns the ID of the connection for which the validation failed.
         *
         * @return the connection ID.
         */
        public Optional<String> getConnectionId() {
            return Optional.ofNullable(connectionId);
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final var that = (Failure) o;
            return Objects.equals(command, that.command) &&
                    Objects.equals(commandResponse, that.commandResponse) &&
                    Objects.equals(detailMessage, that.detailMessage);
        }

        @Override
        public int hashCode() {
            return Objects.hash(command, commandResponse, detailMessage);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "command=" + command +
                    ", commandResponse=" + commandResponse +
                    ", detailMessage=" + detailMessage +
                    "]";
        }

    }

}
