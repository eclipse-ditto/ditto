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
package org.eclipse.ditto.internal.models.acks;

import static org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel.LIVE_RESPONSE;
import static org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel.TWIN_PERSISTED;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.exceptions.CommandTimeoutException;
import org.eclipse.ditto.internal.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.internal.models.signal.CommandHeaderRestoration;
import org.eclipse.ditto.internal.models.signal.correlation.CommandAndCommandResponseMatchingValidator;
import org.eclipse.ditto.internal.models.signal.correlation.MatchingValidationResult;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.acks.ThingAcknowledgementFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;

import akka.actor.AbstractActorWithTimers;
import akka.actor.Props;

/**
 * Actor which is created for an {@code ThingModifyCommand} containing {@code AcknowledgementRequests} responsible for
 * building an {@link AcknowledgementAggregator}, e.g. timing it out when not all requested acknowledgements were
 * received after the {@code timeout} contained in the passed thing modify command.
 *
 * @since 1.1.0
 */
public final class AcknowledgementAggregatorActor extends AbstractActorWithTimers {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration SMART_CHANNEL_BUFFER = Duration.ofSeconds(10);

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final String correlationId;
    private final Signal<?> originatingSignal;
    private final AcknowledgementAggregator ackregator;
    private final Consumer<Object> responseSignalConsumer;
    private final Duration timeout;
    private final Consumer<MatchingValidationResult.Failure> matchingValidationFailureConsumer;
    private Function<Acknowledgements, ThingErrorResponse> getAsTimeoutErrorResponse;

    @SuppressWarnings("java:S1144")
    private AcknowledgementAggregatorActor(final EntityId entityId,
            final Signal<?> originatingSignal,
            @Nullable final Duration timeoutOverride,
            final Duration maxTimeout,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer,
            @Nullable final Consumer<MatchingValidationResult.Failure> matchingValidationFailureConsumer) {

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
        getAsTimeoutErrorResponse = getDefaultGetAsTimeoutErrorResponse();

        final var acknowledgementRequests = signalDittoHeaders.getAcknowledgementRequests();
        ackregator = AcknowledgementAggregator.getInstance(entityId, correlationId, timeout, headerTranslator);
        ackregator.addAcknowledgementRequests(acknowledgementRequests);
        log.withCorrelationId(correlationId)
                .info("Starting to wait for all requested acknowledgements <{}> for a maximum duration of <{}>.",
                        acknowledgementRequests, timeout);
    }

    private Function<Acknowledgements, ThingErrorResponse> getDefaultGetAsTimeoutErrorResponse() {
        return aggregatedAcknowledgements -> {
            final var thingId = ThingId.of(aggregatedAcknowledgements.getEntityId());
            final var gatewayCommandTimeoutException = CommandTimeoutException.newBuilder(timeout)
                    .dittoHeaders(aggregatedAcknowledgements.getDittoHeaders())
                    .build();

            return ThingErrorResponse.of(thingId, gatewayCommandTimeoutException);
        };
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
     * @return the Akka configuration Props object.
     * @throws org.eclipse.ditto.base.model.acks.AcknowledgementRequestParseException if a contained acknowledgement
     * request could not be parsed.
     */
    static Props props(final EntityId entityId,
            final Signal<?> signal,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer,
            @Nullable final Consumer<MatchingValidationResult.Failure> matchingValidationFailureConsumer) {

        return Props.create(AcknowledgementAggregatorActor.class,
                entityId,
                signal,
                null,
                acknowledgementConfig.getForwarderFallbackTimeout(),
                headerTranslator,
                responseSignalConsumer,
                matchingValidationFailureConsumer);
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
     * @return the Akka configuration Props object.
     * @throws org.eclipse.ditto.base.model.acks.AcknowledgementRequestParseException if a contained acknowledgement
     * request could not be parsed.
     */
    static Props props(final EntityId entityId,
            final Signal<?> signal,
            @Nullable final Duration timeoutOverride,
            final Duration maxTimeout,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer,
            @Nullable final Consumer<MatchingValidationResult.Failure> matchingValidationFailureConsumer) {

        return Props.create(AcknowledgementAggregatorActor.class,
                entityId,
                signal,
                timeoutOverride,
                maxTimeout,
                headerTranslator,
                responseSignalConsumer,
                matchingValidationFailureConsumer);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ThingCommandResponse.class, this::handleThingCommandResponse)
                .match(MessageCommandResponse.class, this::handleMessageCommandResponse)
                .match(Acknowledgement.class, this::handleAcknowledgement)
                .match(Acknowledgements.class, this::handleAcknowledgements)
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .match(Control.class, Control.WAITING_FOR_ACKS_TIMED_OUT::equals, this::handleReceiveTimeout)
                .matchAny(m -> log.warning("Received unexpected message: <{}>", m))
                .build();
    }

    private void handleThingCommandResponse(final ThingCommandResponse<?> thingCommandResponse) {
        log.withCorrelationId(correlationId).debug("Received thing command response <{}>.", thingCommandResponse);
        final var commandResponseValidationResult = validateResponse(thingCommandResponse);
        if (commandResponseValidationResult.isSuccess()) {
            final var acknowledgementLabel = getAckLabelOfResponse(originatingSignal);
            addCommandResponse(thingCommandResponse, getAcknowledgement(thingCommandResponse, acknowledgementLabel));
        } else {
            handleMatchingValidationFailure(commandResponseValidationResult.asFailureOrThrow());
        }
    }

    private MatchingValidationResult validateResponse(final CommandResponse<?> commandResponse) {
        final MatchingValidationResult result;
        if (Command.isLiveCommand(originatingSignal) || ThingCommand.isChannelSmart(originatingSignal)) {
            result = validateLiveResponse((Command<?>) originatingSignal, commandResponse);
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

    private static Acknowledgement getAcknowledgement(final ThingCommandResponse<?> thingCommandResponse,
            final AcknowledgementLabel acknowledgementLabel) {

        return ThingAcknowledgementFactory.newAcknowledgement(acknowledgementLabel,
                thingCommandResponse.getEntityId(),
                thingCommandResponse.getHttpStatus(),
                thingCommandResponse.getDittoHeaders(),
                getPayload(thingCommandResponse).orElse(null));
    }

    private static Optional<JsonValue> getPayload(final ThingCommandResponse<?> thingCommandResponse) {
        final Optional<JsonValue> result;
        if (thingCommandResponse instanceof WithOptionalEntity withOptionalEntity) {
            result = withOptionalEntity.getEntity(thingCommandResponse.getImplementedSchemaVersion());
        } else {
            result = Optional.empty();
        }

        return result;
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

        getAsTimeoutErrorResponse = getInvalidLiveResponseReceivedGetAsTimeoutErrorResponse(detailMessage);

        matchingValidationFailureConsumer.accept(matchingValidationFailure);
    }

    private Function<Acknowledgements, ThingErrorResponse> getInvalidLiveResponseReceivedGetAsTimeoutErrorResponse(
            final String detailMessage
    ) {
        return acknowledgements -> {
            final var descriptionPattern = "Received no appropriate live response within the specified timeout." +
                    " An invalid response was received, though: {0}";
            final var gatewayCommandTimeoutException = CommandTimeoutException.newBuilder(timeout)
                    .dittoHeaders(acknowledgements.getDittoHeaders())
                    .description(MessageFormat.format(descriptionPattern, detailMessage))
                    .build();
            return WithEntityId.getEntityId(originatingSignal)
                    .map(ThingId::of)
                    .map(thingId -> ThingErrorResponse.of(thingId, gatewayCommandTimeoutException))
                    .orElseGet(() -> ThingErrorResponse.of(gatewayCommandTimeoutException));
        };
    }

    private void handleMessageCommandResponse(final MessageCommandResponse<?, ?> messageCommandResponse) {
        log.withCorrelationId(correlationId).debug("Received message command response <{}>.", messageCommandResponse);
        final var commandResponseValidationResult = validateResponse(messageCommandResponse);
        if (commandResponseValidationResult.isSuccess()) {
            addCommandResponse(messageCommandResponse, getAcknowledgement(messageCommandResponse));
        } else {
            handleMatchingValidationFailure(commandResponseValidationResult.asFailureOrThrow());
        }
    }

    private static Acknowledgement getAcknowledgement(final MessageCommandResponse<?, ?> messageCommandResponse) {
        final var responseDittoHeaders = messageCommandResponse.getDittoHeaders();
        final var message = messageCommandResponse.getMessage();
        final var liveResponseAckHeaders = responseDittoHeaders.toBuilder()
                .putHeaders(message.getHeaders())
                .build();

        return ThingAcknowledgementFactory.newAcknowledgement(LIVE_RESPONSE,
                messageCommandResponse.getEntityId(),
                messageCommandResponse.getHttpStatus(),
                liveResponseAckHeaders,
                getPayload(messageCommandResponse).orElse(null));
    }

    private static Optional<JsonValue> getPayload(final MessageCommandResponse<?, ?> messageCommandResponse) {
        final var jsonMessagePointer = MessageCommandResponse.JsonFields.JSON_MESSAGE.getPointer();
        final var jsonMessagePayloadPointer = MessageCommandResponse.JsonFields.JSON_MESSAGE_PAYLOAD.getPointer();
        final var messagePayloadPointer = jsonMessagePointer.append(jsonMessagePayloadPointer);
        final var messageCommandResponseJsonObject = messageCommandResponse.toJson();

        return messageCommandResponseJsonObject.getValue(messagePayloadPointer);
    }

    private void handleReceiveTimeout(final Control receiveTimeout) {
        log.withCorrelationId(correlationId).info("Timed out waiting for all requested acknowledgements, " +
                "completing Acknowledgements with timeouts...");
        completeAcknowledgements(null);
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
        // abort on DittoRuntimeException
        handleSignal(dittoRuntimeException);
        getContext().stop(getSelf());
    }

    private void potentiallyCompleteAcknowledgements(@Nullable final CommandResponse<?> response) {
        if (ackregator.receivedAllRequestedAcknowledgements()) {
            completeAcknowledgements(response);
        }
    }

    private void completeAcknowledgements(@Nullable final CommandResponse<?> response) {
        final var aggregatedAcknowledgements =
                ackregator.getAggregatedAcknowledgements(originatingSignal.getDittoHeaders());
        final var builtInAcknowledgementOnly = containsOnlyTwinPersistedOrLiveResponse(aggregatedAcknowledgements);
        if (null != response && builtInAcknowledgementOnly) {

            // In this case, only the implicit "twin-persisted" acknowledgement was asked for, respond with the signal:
            handleSignal(response);
        } else if (builtInAcknowledgementOnly && !ackregator.receivedAllRequestedAcknowledgements()) {

            // There is no response. Sending an error according to channel.
            handleSignal(getAsTimeoutErrorResponse.apply(aggregatedAcknowledgements));
        } else {
            log.withCorrelationId(originatingSignal)
                    .debug("Completing with collected acknowledgements: {}", aggregatedAcknowledgements);
            handleSignal(aggregatedAcknowledgements);
        }
        getContext().stop(getSelf());
    }

    private void handleSignal(final DittoHeadersSettable<?> signal) {
        responseSignalConsumer.accept(
                CommandHeaderRestoration.restoreCommandConnectivityHeaders(signal, originatingSignal.getDittoHeaders()));
    }

    private static boolean containsOnlyTwinPersistedOrLiveResponse(final Acknowledgements aggregatedAcknowledgements) {
        return aggregatedAcknowledgements.getSize() == 1 &&
                aggregatedAcknowledgements.stream()
                        .anyMatch(ack -> {
                            final var label = ack.getLabel();
                            return TWIN_PERSISTED.equals(label) || LIVE_RESPONSE.equals(label);
                        });
    }

    private static AcknowledgementLabel getAckLabelOfResponse(final Signal<?> signal) {
        // check originating signal for ack label of response
        // because live commands may generate twin responses due to live timeout fallback strategy
        // smart channel commands are treated as live channel commands
        final var isChannelLive = Signal.isChannelLive(signal);
        final var isChannelSmart = ThingCommand.isChannelSmart(signal);
        return isChannelLive || isChannelSmart ? LIVE_RESPONSE : TWIN_PERSISTED;
    }

    private static Duration getTimeout(final Signal<?> originatingSignal, final Duration maxTimeout,
            @Nullable final Duration specifiedTimeout) {
        if (specifiedTimeout != null) {
            return specifiedTimeout;
        } else if (ThingCommand.isChannelSmart(originatingSignal)) {
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
