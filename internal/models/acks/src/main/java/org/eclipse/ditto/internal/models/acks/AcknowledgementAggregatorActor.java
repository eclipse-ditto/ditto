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

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayCommandTimeoutException;
import org.eclipse.ditto.internal.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.internal.models.signal.SignalInformationPoint;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.protocol.HeaderTranslator;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.acks.ThingAcknowledgementFactory;
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

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final String correlationId;
    private final DittoHeaders requestCommandHeaders;
    private final AcknowledgementAggregator ackregator;
    private final Consumer<Object> responseSignalConsumer;
    private final Duration timeout;

    private AcknowledgementAggregatorActor(final EntityId entityId,
            final DittoHeaders dittoHeaders,
            final Duration maxTimeout,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer) {

        this.responseSignalConsumer = responseSignalConsumer;
        requestCommandHeaders = dittoHeaders;
        correlationId = requestCommandHeaders.getCorrelationId()
                .orElseGet(() ->
                        // fall back using the actor name which also contains the correlation-id
                        getSelf().path().name()
                );

        timeout = getTimeout(requestCommandHeaders, maxTimeout);
        timers().startSingleTimer(Control.WAITING_FOR_ACKS_TIMED_OUT, Control.WAITING_FOR_ACKS_TIMED_OUT, timeout);

        final var acknowledgementRequests = requestCommandHeaders.getAcknowledgementRequests();
        ackregator = AcknowledgementAggregator.getInstance(entityId, correlationId, timeout, headerTranslator);
        ackregator.addAcknowledgementRequests(acknowledgementRequests);
        log.withCorrelationId(correlationId)
                .info("Starting to wait for all requested acknowledgements <{}> for a maximum duration of <{}>.",
                        acknowledgementRequests, timeout);
    }

    /**
     * Creates Akka configuration object Props for this AcknowledgementAggregatorActor.
     *
     * @param entityId the entity ID of the originating signal.
     * @param dittoHeaders the ditto headers of the originating signal.
     * @param acknowledgementConfig provides configuration setting regarding acknowledgement handling.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param responseSignalConsumer a consumer which is invoked with the response signal, e.g. in order to send the
     * response over a channel to the user.
     * @return the Akka configuration Props object.
     * @throws org.eclipse.ditto.base.model.acks.AcknowledgementRequestParseException if a contained acknowledgement
     * request could not be parsed.
     */
    static Props props(final EntityId entityId,
            final DittoHeaders dittoHeaders,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer) {

        return Props.create(AcknowledgementAggregatorActor.class,
                entityId,
                dittoHeaders,
                acknowledgementConfig.getForwarderFallbackTimeout(),
                headerTranslator,
                responseSignalConsumer);
    }

    /**
     * Creates Akka configuration object Props for this AcknowledgementAggregatorActor.
     *
     * @param entityId the entity ID of the originating signal.
     * @param dittoHeaders the ditto headers of the originating signal.
     * @param maxTimeout the maximum timeout of acknowledgement aggregation.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param responseSignalConsumer a consumer which is invoked with the response signal, e.g. in order to send the
     * response over a channel to the user.
     * @return the Akka configuration Props object.
     * @throws org.eclipse.ditto.base.model.acks.AcknowledgementRequestParseException if a contained acknowledgement
     * request could not be parsed.
     */
    static Props props(final EntityId entityId,
            final DittoHeaders dittoHeaders,
            final Duration maxTimeout,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer) {

        return Props.create(AcknowledgementAggregatorActor.class,
                entityId,
                dittoHeaders,
                maxTimeout,
                headerTranslator,
                responseSignalConsumer);
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
        final var acknowledgementLabel =
                SignalInformationPoint.isChannelLive(thingCommandResponse) ? LIVE_RESPONSE : TWIN_PERSISTED;
        addCommandResponse(thingCommandResponse, getAcknowledgement(thingCommandResponse, acknowledgementLabel));
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
        if (thingCommandResponse instanceof WithOptionalEntity) {
            final var withOptionalEntity = (WithOptionalEntity) thingCommandResponse;
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

    private void handleMessageCommandResponse(final MessageCommandResponse<?, ?> messageCommandResponse) {
        log.withCorrelationId(correlationId).debug("Received message command response <{}>.", messageCommandResponse);
        addCommandResponse(messageCommandResponse, getAcknowledgement(messageCommandResponse));
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
        final var aggregatedAcknowledgements = ackregator.getAggregatedAcknowledgements(requestCommandHeaders);
        final var builtInAcknowledgementOnly = containsOnlyTwinPersistedOrLiveResponse(aggregatedAcknowledgements);
        if (null != response && builtInAcknowledgementOnly) {
            // in this case, only the implicit "twin-persisted" acknowledgement was asked for, respond with the signal:
            handleSignal(response);
        } else if (builtInAcknowledgementOnly && !ackregator.receivedAllRequestedAcknowledgements()) {
            // there is no response. send an error according to channel
            handleSignal(asThingErrorResponse(aggregatedAcknowledgements));
        } else {
            log.withCorrelationId(requestCommandHeaders)
                    .debug("Completing with collected acknowledgements: {}", aggregatedAcknowledgements);
            handleSignal(aggregatedAcknowledgements);
        }
        getContext().stop(getSelf());
    }

    public static DittoHeadersSettable<?> restoreCommandConnectivityHeaders(final DittoHeadersSettable<?> signal,
            final DittoHeaders requestCommandHeaders) {

        final var signalDittoHeaders = signal.getDittoHeaders();
        final var enhancedHeadersBuilder = signalDittoHeaders.toBuilder()
                .removeHeader(DittoHeaderDefinition.EXPECTED_RESPONSE_TYPES.getKey())
                .removeHeader(DittoHeaderDefinition.INBOUND_PAYLOAD_MAPPER.getKey())
                .removeHeader(DittoHeaderDefinition.REPLY_TARGET.getKey());
        if (requestCommandHeaders.containsKey(DittoHeaderDefinition.EXPECTED_RESPONSE_TYPES.getKey())) {
            enhancedHeadersBuilder.expectedResponseTypes(requestCommandHeaders.getExpectedResponseTypes());
        }
        requestCommandHeaders.getInboundPayloadMapper().ifPresent(enhancedHeadersBuilder::inboundPayloadMapper);
        requestCommandHeaders.getReplyTarget().ifPresent(enhancedHeadersBuilder::replyTarget);
        return signal.setDittoHeaders(enhancedHeadersBuilder.build());
    }

    private void handleSignal(final DittoHeadersSettable<?> signal) {
        responseSignalConsumer.accept(restoreCommandConnectivityHeaders(signal, requestCommandHeaders));
    }

    /**
     * Convert aggregated acknowledgements to a single error response in case only built-in acknowledgements
     * are requested.
     *
     * @param aggregatedAcknowledgements the aggregated acknowledgements.
     * @return the error response.
     */
    private ThingErrorResponse asThingErrorResponse(final Acknowledgements aggregatedAcknowledgements) {
        final var thingId = ThingId.of(aggregatedAcknowledgements.getEntityId());
        final DittoRuntimeException dittoRuntimeException = GatewayCommandTimeoutException.newBuilder(timeout)
                .dittoHeaders(aggregatedAcknowledgements.getDittoHeaders())
                .build();

        return ThingErrorResponse.of(thingId, dittoRuntimeException);
    }

    private static boolean containsOnlyTwinPersistedOrLiveResponse(final Acknowledgements aggregatedAcknowledgements) {
        return aggregatedAcknowledgements.getSize() == 1 &&
                aggregatedAcknowledgements.stream()
                        .anyMatch(ack -> {
                            final var label = ack.getLabel();
                            return TWIN_PERSISTED.equals(label) ||
                                    LIVE_RESPONSE.equals(label);
                        });
    }

    private static Duration getTimeout(final DittoHeaders headers, final Duration maxTimeout) {
        return headers.getTimeout()
                .filter(timeout -> timeout.minus(maxTimeout).isNegative())
                .orElse(maxTimeout);
    }

    private enum Control {
        WAITING_FOR_ACKS_TIMED_OUT;
    }

}
