/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.edge.service.acknowledgements;

import static org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel.LIVE_RESPONSE;
import static org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel.TWIN_PERSISTED;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.CommandResponseAcknowledgementProvider;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.exceptions.CommandTimeoutException;
import org.eclipse.ditto.base.service.acknowledgements.AcknowledgementAggregator;
import org.eclipse.ditto.internal.models.signal.CommandHeaderRestoration;
import org.eclipse.ditto.internal.models.signal.correlation.CommandAndCommandResponseMatchingValidator;
import org.eclipse.ditto.internal.models.signal.correlation.MatchingValidationResult;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;

import akka.actor.AbstractActorWithTimers;
import akka.actor.Props;

/**
 * Actor which is created for an {@code ThingModifyCommand} containing {@code AcknowledgementRequests} responsible for
 * building an {@link org.eclipse.ditto.base.service.acknowledgements.AcknowledgementAggregator}, e.g. timing it out when not all requested acknowledgements were
 * received after the {@code timeout} contained in the passed thing modify command.
 *
 * @since 1.1.0
 */
public final class AcknowledgementAggregatorActor<C extends Command<C>> extends AbstractActorWithTimers {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration SMART_CHANNEL_BUFFER = Duration.ofSeconds(10);

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final String correlationId;
    private final C originatingSignal;
    private final AcknowledgementAggregator ackregator;
    private final Consumer<Object> responseSignalConsumer;
    private final Duration timeout;
    private final Consumer<MatchingValidationResult.Failure> matchingValidationFailureConsumer;
    private final CommandResponseAcknowledgementProvider<C> acknowledgementProvider;
    private BiFunction<Acknowledgements, DittoHeaders, DittoRuntimeException> getAsTimeoutError;

    @SuppressWarnings("unused")
    private AcknowledgementAggregatorActor(final EntityId entityId,
            final C originatingSignal,
            @Nullable final Duration timeoutOverride,
            final Duration maxTimeout,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer,
            @Nullable final Consumer<MatchingValidationResult.Failure> matchingValidationFailureConsumer,
            final CommandResponseAcknowledgementProvider<C> responseAcknowledgementProvider) {

        this.responseSignalConsumer = responseSignalConsumer;
        this.originatingSignal = originatingSignal;
        final var signalDittoHeaders = originatingSignal.getDittoHeaders();
        correlationId = signalDittoHeaders.getCorrelationId()
                .orElseGet(() ->
                        // fall back using the actor name which also contains the correlation-id
                        getSelf().path().name()
                );

        timeout = getTimeout(originatingSignal, maxTimeout, timeoutOverride);
        this.matchingValidationFailureConsumer = Objects.requireNonNullElseGet(
                matchingValidationFailureConsumer,
                this::getDefaultMatchingValidationFailureConsumer
        );
        timers().startSingleTimer(Control.WAITING_FOR_ACKS_TIMED_OUT, Control.WAITING_FOR_ACKS_TIMED_OUT, timeout);
        getAsTimeoutError = getDefaultGetAsTimeoutError(originatingSignal instanceof WithEntityId withEntityId ?
                withEntityId.getEntityId() : null);

        final var acknowledgementRequests = signalDittoHeaders.getAcknowledgementRequests();
        ackregator = AcknowledgementAggregator.getInstance(entityId, correlationId, timeout, headerTranslator);
        ackregator.addAcknowledgementRequests(acknowledgementRequests);
        log.withCorrelationId(correlationId)
                .info("Starting to wait for all requested acknowledgements <{}> for a maximum duration of <{}>.",
                        acknowledgementRequests, timeout);

        acknowledgementProvider = responseAcknowledgementProvider;
    }

    private BiFunction<Acknowledgements, DittoHeaders, DittoRuntimeException> getDefaultGetAsTimeoutError(
            @Nullable final EntityId entityId) {

        return (aggregatedAcknowledgements, additionalDittoHeaders) -> CommandTimeoutException.newBuilder(timeout)
                .dittoHeaders(
                        calculateHeadersWithEntityId(entityId, aggregatedAcknowledgements, additionalDittoHeaders))
                .build();
    }

    private static DittoHeaders calculateHeadersWithEntityId(@Nullable final EntityId entityId,
            final WithDittoHeaders withDittoHeaders, final DittoHeaders additionalDittoHeaders) {

        final DittoHeadersBuilder<?, ?> dittoHeadersBuilder =
                withDittoHeaders.getDittoHeaders().toBuilder().putHeaders(additionalDittoHeaders);
        if (null != entityId) {
            return dittoHeadersBuilder
                    .putHeader(DittoHeaderDefinition.ENTITY_ID.getKey(), entityId.getEntityType() + ":" + entityId)
                    .build();
        } else {
            return dittoHeadersBuilder.build();
        }
    }

    private Consumer<MatchingValidationResult.Failure> getDefaultMatchingValidationFailureConsumer() {
        return failure -> log.withCorrelationId(originatingSignal)
                .warning("No {} consumer provided." +
                                " Thus no further processing of response validation failure is going to happen.",
                        MatchingValidationResult.Failure.class.getSimpleName());
    }

    /**
     * Creates Akka configuration object Props for this AcknowledgementAggregatorActor.
     *
     * @param entityId the entity ID of the originating signal.
     * @param signal the originating signal.
     * @param acknowledgementConfig provides configuration setting regarding acknowledgement handling.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param responseSignalConsumer a consumer which is invoked with the response signal, e.g. in order to send the
     * response over a channel to the user.
     * @param matchingValidationFailureConsumer optional handler for response validation failures.
     * @param responseAcknowledgementProvider an Acknowledgement provider which provide Acks based on processed command
     * responses or ditto runtime exceptions.
     * @return the Akka configuration Props object.
     * @throws org.eclipse.ditto.base.model.acks.AcknowledgementRequestParseException if a contained acknowledgement
     * request could not be parsed.
     */
    static <C extends Command<?>> Props props(final EntityId entityId,
            final C signal,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer,
            @Nullable final Consumer<MatchingValidationResult.Failure> matchingValidationFailureConsumer,
            final CommandResponseAcknowledgementProvider<C> responseAcknowledgementProvider) {

        return Props.create(AcknowledgementAggregatorActor.class,
                entityId,
                signal,
                null,
                acknowledgementConfig.getForwarderFallbackTimeout(),
                headerTranslator,
                responseSignalConsumer,
                matchingValidationFailureConsumer,
                responseAcknowledgementProvider);
    }

    /**
     * Creates Akka configuration object Props for this AcknowledgementAggregatorActor.
     *
     * @param entityId the entity ID of the originating signal.
     * @param signal the originating signal.
     * @param timeoutOverride the duration to override the timeout of the signal.
     * @param maxTimeout the maximum timeout of acknowledgement aggregation.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param responseSignalConsumer a consumer which is invoked with the response signal, e.g. in order to send the
     * response over a channel to the user.
     * @param matchingValidationFailureConsumer optional handler for response validation failures.
     * @param responseAcknowledgementProvider an Acknowledgement providers which provide Acks based on
     * processed command responses or ditto runtime exceptions.
     * @return the Akka configuration Props object.
     * @throws org.eclipse.ditto.base.model.acks.AcknowledgementRequestParseException if a contained acknowledgement
     * request could not be parsed.
     */
    static <C extends Command<?>> Props props(final EntityId entityId,
            final C signal,
            @Nullable final Duration timeoutOverride,
            final Duration maxTimeout,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer,
            @Nullable final Consumer<MatchingValidationResult.Failure> matchingValidationFailureConsumer,
            final CommandResponseAcknowledgementProvider<C> responseAcknowledgementProvider) {

        return Props.create(AcknowledgementAggregatorActor.class,
                entityId,
                signal,
                timeoutOverride,
                maxTimeout,
                headerTranslator,
                responseSignalConsumer,
                matchingValidationFailureConsumer,
                responseAcknowledgementProvider);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Acknowledgement.class, this::handleAcknowledgement)
                .match(Acknowledgements.class, this::handleAcknowledgements)
                .match(CommandResponse.class, this::handleCommandResponse)
                .match(CommandTimeoutException.class, this::handleCommandTimeoutException)
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .matchEquals(Control.WAITING_FOR_ACKS_TIMED_OUT, this::handleReceiveTimeout)
                .matchAny(m -> log.warning("Received unexpected message: <{}>", m))
                .build();
    }

    private void addCommandResponse(final CommandResponse<?> commandResponse, final Acknowledgement acknowledgement) {
        ackregator.addReceivedAcknowledgment(acknowledgement);
        potentiallyCompleteAcknowledgements(commandResponse);
    }

    private void handleMatchingValidationFailure(final MatchingValidationResult.Failure matchingValidationFailure) {
        final var detailMessage = matchingValidationFailure.getDetailMessage();

        log.withCorrelationId(originatingSignal)
                .warning("Received invalid response. Reason: {} Response: {}.",
                        detailMessage,
                        matchingValidationFailure.getCommandResponse());

        getAsTimeoutError = getInvalidLiveResponseReceivedGetAsTimeoutError(detailMessage);

        matchingValidationFailureConsumer.accept(matchingValidationFailure);
    }

    private BiFunction<Acknowledgements, DittoHeaders, DittoRuntimeException> getInvalidLiveResponseReceivedGetAsTimeoutError(
            final String detailMessage
    ) {
        return (acknowledgements, additionalDittoHeaders) -> {
            final var descriptionPattern = "Received no appropriate live response within the specified timeout." +
                    " An invalid response was received, though: {0}";
            return CommandTimeoutException.newBuilder(timeout)
                    .dittoHeaders(calculateHeadersWithEntityId(acknowledgements.getEntityId(), acknowledgements,
                            additionalDittoHeaders))
                    .description(MessageFormat.format(descriptionPattern, detailMessage))
                    .build();
        };
    }

    private void handleCommandResponse(final CommandResponse<?> commandResponse) {
        log.withCorrelationId(correlationId).debug("Received command response <{}>.", commandResponse);
        final var commandResponseValidationResult = validateResponse(commandResponse);
        if (commandResponseValidationResult.isSuccess()) {
            addCommandResponse(commandResponse, provideAcknowledgement(commandResponse));
        } else {
            handleMatchingValidationFailure(commandResponseValidationResult.asFailureOrThrow());
        }
    }

    private MatchingValidationResult validateResponse(final CommandResponse<?> commandResponse) {
        final MatchingValidationResult result;
        if (Command.isLiveCommand(originatingSignal) || Signal.isChannelSmart(originatingSignal)) {
            result = validateLiveResponse(originatingSignal, commandResponse);
        } else {

            // Non-live responses are supposed to be valid as they are generated by Ditto itself.
            result = MatchingValidationResult.success();
        }

        return result;
    }

    private static MatchingValidationResult validateLiveResponse(final Command<?> command,
            final CommandResponse<?> commandResponse) {

        final var responseMatchingValidator =
                CommandAndCommandResponseMatchingValidator.getInstance();

        return responseMatchingValidator.apply(command, commandResponse);
    }

    private void handleReceiveTimeout(final Control receiveTimeout) {
        log.withCorrelationId(correlationId).info("Timed out waiting for all requested acknowledgements, " +
                "completing Acknowledgements with timeouts...");
        completeAcknowledgements(null, DittoHeaders.empty());
    }

    private void handleAcknowledgement(final Acknowledgement acknowledgement) {
        log.withCorrelationId(correlationId).debug("Received acknowledgement <{}>.", acknowledgement);
        ackregator.addReceivedAcknowledgment(acknowledgement);
        potentiallyCompleteAcknowledgements(null);
    }

    private void handleAcknowledgements(final Acknowledgements acknowledgements) {
        log.withCorrelationId(correlationId).debug("Received acknowledgements <{}>.", acknowledgements);
        acknowledgements.stream().forEach(ackregator::addReceivedAcknowledgment);
        potentiallyCompleteAcknowledgements(null);
    }

    private void handleDittoRuntimeException(final DittoRuntimeException dittoRuntimeException) {
        log.withCorrelationId(correlationId)
                .info("Stopped waiting for acknowledgements because of ditto runtime exception <{}>.",
                        dittoRuntimeException);
        handleSignal(dittoRuntimeException);
        getContext().stop(getSelf());
    }

    private void handleCommandTimeoutException(final CommandTimeoutException commandTimeoutException) {
        log.withCorrelationId(correlationId).info("Timed out waiting for all requested acknowledgements, " +
                "completing Acknowledgements with timeouts...");
        completeAcknowledgements(null, commandTimeoutException.getDittoHeaders());
    }

    private Acknowledgement provideAcknowledgement(final CommandResponse<?> commandResponse) {
        if (acknowledgementProvider.isApplicable(commandResponse)) {
            return acknowledgementProvider.provideAcknowledgement(originatingSignal, commandResponse);
        } else if (commandResponse instanceof Acknowledgement acknowledgement) {
            return acknowledgement;
        } else {
            log.withCorrelationId(originatingSignal)
                    .error("Unknown response to transform to Acknowledgement: {}", commandResponse.getType());
            throw DittoInternalErrorException.newBuilder().dittoHeaders(originatingSignal.getDittoHeaders())
                    .build();
        }
    }

    private void potentiallyCompleteAcknowledgements(@Nullable final CommandResponse<?> response) {
        if (ackregator.receivedAllRequestedAcknowledgements()) {
            completeAcknowledgements(response, DittoHeaders.empty());
        }
    }

    private void completeAcknowledgements(@Nullable final CommandResponse<?> response,
            final DittoHeaders additionalDittoHeaders) {
        final var aggregatedAcknowledgements =
                ackregator.getAggregatedAcknowledgements(originatingSignal.getDittoHeaders());
        final var builtInAcknowledgementOnly = containsOnlyTwinPersistedOrLiveResponse(aggregatedAcknowledgements);
        if (null != response && builtInAcknowledgementOnly) {

            // In this case, only the implicit "twin-persisted" acknowledgement was asked for, respond with the signal:
            handleSignal(response);
        } else if (builtInAcknowledgementOnly && !ackregator.receivedAllRequestedAcknowledgements()) {

            // There is no response. Sending an error according to channel.
            handleSignal(getAsTimeoutError.apply(aggregatedAcknowledgements, additionalDittoHeaders));
        } else {
            log.withCorrelationId(originatingSignal)
                    .debug("Completing with collected acknowledgements: {}", aggregatedAcknowledgements);
            handleSignal(aggregatedAcknowledgements);
        }
        getContext().stop(getSelf());
    }

    private void handleSignal(final DittoHeadersSettable<?> signal) {
        responseSignalConsumer.accept(
                CommandHeaderRestoration.restoreCommandConnectivityHeaders(signal,
                        originatingSignal.getDittoHeaders()));
    }

    private static boolean containsOnlyTwinPersistedOrLiveResponse(final Acknowledgements aggregatedAcknowledgements) {
        return aggregatedAcknowledgements.getSize() == 1 &&
                aggregatedAcknowledgements.stream()
                        .anyMatch(ack -> {
                            final var label = ack.getLabel();
                            return TWIN_PERSISTED.equals(label) || LIVE_RESPONSE.equals(label);
                        });
    }

    private static Duration getTimeout(final Signal<?> originatingSignal, final Duration maxTimeout,
            @Nullable final Duration specifiedTimeout) {

        if (specifiedTimeout != null) {
            return specifiedTimeout;
        } else if (Signal.isChannelSmart(originatingSignal)) {
            return originatingSignal.getDittoHeaders().getTimeout().orElse(COMMAND_TIMEOUT)
                    .plus(SMART_CHANNEL_BUFFER);
        } else {
            return originatingSignal.getDittoHeaders().getTimeout()
                    .filter(timeout1 -> timeout1.minus(maxTimeout).isNegative())
                    .orElse(maxTimeout);
        }
    }

    private enum Control {
        WAITING_FOR_ACKS_TIMED_OUT
    }

}
