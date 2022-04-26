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

import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.LogCategory;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.model.LogLevel;
import org.eclipse.ditto.connectivity.model.LogType;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.mappingstrategies.IllegalAdaptableException;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Factory for creating instances of {@link LogEntry}.
 *
 * @since 2.3.0
 */
@Immutable
public final class LogEntryFactory {

    /**
     * The fallback correlation ID to be used if no other was provided.
     */
    public static final String FALLBACK_CORRELATION_ID = "<not-provided>";

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
     * @throws IllegalArgumentException if {@code detailMessage} is empty or blank.
     */
    public static LogEntry getLogEntryForFailedCommandResponseRoundTrip(final Command<?> command,
            final CommandResponse<?> commandResponse,
            final String detailMessage) {

        ConditionChecker.checkNotNull(command, "command");
        ConditionChecker.checkNotNull(commandResponse, "commandResponse");

        final var logEntryBuilder = ConnectivityModelFactory.newLogEntryBuilder(
                getCorrelationId(command).or(() -> getCorrelationId(commandResponse)).orElse(FALLBACK_CORRELATION_ID),
                Instant.now(),
                LogCategory.RESPONSE,
                LogType.DROPPED,
                LogLevel.FAILURE,
                validateDetailMessage(detailMessage)
        );

        getEntityId(command).or(() -> getEntityId(commandResponse)).ifPresent(logEntryBuilder::entityId);

        return logEntryBuilder.build();
    }

    private static String validateDetailMessage(final String detailMessage) {
        return ConditionChecker.checkArgument(ConditionChecker.checkNotNull(detailMessage, "detailMessage"),
                Predicate.not(String::isBlank),
                () -> "The detailMessage must not be blank.");
    }

    private static Optional<String> getCorrelationId(final WithDittoHeaders withDittoHeaders) {
        final var dittoHeaders = withDittoHeaders.getDittoHeaders();
        return dittoHeaders.getCorrelationId();
    }

    private static Optional<EntityId> getEntityId(final Signal<?> signal) {
        return WithEntityId.getEntityId(signal);
    }

    /**
     * Returns a {@code LogEntry} for an invalid {@code CommandResponse} which led to
     * an {@code IllegalAdaptableException}.
     * The failure is described by the specified detail message string argument.
     *
     * @param illegalAdaptableException the exception that indicates that an {@code Adaptable} of a
     * {@code CommandResponse} was invalid for some reason.
     * @param detailMessage describes why the {@code CommandResponse} {@code Adaptable} is illegal.
     * @throws NullPointerException if {@code illegalAdaptableException} is {@code null}.
     * @throws IllegalArgumentException if {@code detailMessage} is empty or blank.
     */
    public static LogEntry getLogEntryForIllegalCommandResponseAdaptable(
            final IllegalAdaptableException illegalAdaptableException,
            final String detailMessage
    ) {
        ConditionChecker.checkNotNull(illegalAdaptableException, "illegalAdaptableException");

        final var logEntryBuilder = ConnectivityModelFactory.newLogEntryBuilder(
                getCorrelationId(illegalAdaptableException).orElse(FALLBACK_CORRELATION_ID),
                Instant.now(),
                LogCategory.RESPONSE,
                LogType.DROPPED,
                LogLevel.FAILURE,
                validateDetailMessage(detailMessage)
        );

        getEntityId(illegalAdaptableException).ifPresent(logEntryBuilder::entityId);

        return logEntryBuilder.build();
    }

    private static Optional<EntityId> getEntityId(final IllegalAdaptableException illegalAdaptableException) {
        return getEntityIdFromTopicPath(illegalAdaptableException.getTopicPath());
    }

    private static Optional<EntityId> getEntityIdFromTopicPath(final TopicPath topicPath) {
        final Optional<EntityId> result;
        final var group = topicPath.getGroup();
        if (TopicPath.Group.THINGS == group) {
            result = Optional.of(ThingId.of(topicPath.getNamespace(), topicPath.getEntityName()));
        } else if (TopicPath.Group.POLICIES == group) {
            result = Optional.of(PolicyId.of(topicPath.getNamespace(), topicPath.getEntityName()));
        } else {
            result = Optional.empty();
        }
        return result;
    }

}
